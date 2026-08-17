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
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
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
import com.sourcetx.companion.ui.theme.SourceTxTheme

@Composable
fun UpdateScreen(
    catalog: HardwareCatalog?,
    isConnected: Boolean,
    isFlashing: Boolean,
    flashPercent: Int,
    flashStatusText: String,
    consoleLog: String,
    successMessage: String?,
    errorMessage: String?,
    onStartUpdate: () -> Unit,
    onNavigateToConfig: (() -> Unit)? = null
) {
    val colors = SourceTxTheme.colors
    val scrollState = rememberScrollState()
    var showConfirmation by remember { mutableStateOf(false) }

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
                        .background(colors.updateBg)
                        .border(1.dp, colors.updateBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GetApp,
                        contentDescription = "Update",
                        tint = colors.updateAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Update or Repair",
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "For an existing SourceTX transmitter",
                        color = colors.updateAccent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            ReleaseChannelSelector()
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Data Retention & Specs Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "Verified stable firmware update",
                    color = colors.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Companion updates the firmware partition while preserving your saved transmitter models and settings.",
                    color = colors.textSecondary,
                    fontSize = 10.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✓ Data Retention: All models preserved",
                        color = GreenSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                    color = colors.updateAccent,
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
                color = colors.updateAccent,
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
                containerColor = colors.updateAccent,
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
                Text(text = "Updating SourceTX...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            } else {
                Text(
                    text = if (isConnected) "Update SourceTX" else "Connect USB OTG to Update",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Update SourceTX?") },
            text = {
                Text("Install the signed stable application update? Compatible models and settings are preserved. Keep USB connected until the app verifies the written firmware and restarts the transmitter.")
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmation = false
                    onStartUpdate()
                }) { Text("Update") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}
