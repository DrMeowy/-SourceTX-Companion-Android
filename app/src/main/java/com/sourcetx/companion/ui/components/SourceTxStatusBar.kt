package com.sourcetx.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourcetx.companion.ui.theme.GreenSuccess
import com.sourcetx.companion.ui.theme.RedDanger
import com.sourcetx.companion.ui.theme.SourceTxTheme
import com.sourcetx.companion.usb.UsbDeviceInfo

@Composable
fun SourceTxStatusBar(
    connectedDevice: UsbDeviceInfo?,
    hasPermission: Boolean,
    version: String,
    isCheckingUpdate: Boolean,
    hasUpdateAvailable: Boolean,
    onCheckUpdate: () -> Unit,
    onReportBug: () -> Unit
) {
    val colors = SourceTxTheme.colors
    val isConnected = connectedDevice != null && hasPermission

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated)
            .border(width = 1.dp, color = colors.border)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Status Dot + Text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) GreenSuccess else RedDanger)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isConnected) "USB ready • ${connectedDevice?.productName}" else "Ready • SourceTX Companion $version",
                color = colors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Actions: Update Button & Bug Report Button
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Check for App Updates Button (Bottom)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (hasUpdateAvailable) colors.accent.copy(alpha = 0.2f) else colors.surface)
                    .border(
                        1.dp,
                        if (hasUpdateAvailable) colors.accent else colors.border,
                        RoundedCornerShape(5.dp)
                    )
                    .clickable(enabled = !isCheckingUpdate) { onCheckUpdate() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            color = colors.accent,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Checking...",
                            color = colors.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Update",
                            tint = if (hasUpdateAvailable) colors.accent else colors.accent,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hasUpdateAvailable) "Update App" else "Check for App Updates",
                            color = colors.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Report a Bug Button (Bottom Right - Red Danger Accent)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(RedDanger.copy(alpha = 0.12f))
                    .border(1.dp, RedDanger.copy(alpha = 0.40f), RoundedCornerShape(5.dp))
                    .clickable { onReportBug() }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Report Bug",
                        tint = RedDanger,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Report Bug",
                        color = RedDanger,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
