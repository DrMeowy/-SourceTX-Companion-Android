package com.sourcetx.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourcetx.companion.ui.theme.GreenSuccess
import com.sourcetx.companion.ui.theme.RedDanger
import com.sourcetx.companion.ui.theme.SourceTxTheme
import com.sourcetx.companion.usb.UsbDeviceInfo

@Composable
fun SourceTxStatusBar(
    connectedDevice: UsbDeviceInfo?,
    hasPermission: Boolean
) {
    val colors = SourceTxTheme.colors
    val isConnected = connectedDevice != null && hasPermission

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(width = 1.dp, color = colors.border)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) GreenSuccess else RedDanger)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isConnected) "Connected • ${connectedDevice?.productName}" else "Disconnected • Plug in USB OTG",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        Text(
            text = "ESP32S3 • 4MB • ST7796U",
            color = colors.textMuted,
            fontSize = 10.sp
        )
    }
}
