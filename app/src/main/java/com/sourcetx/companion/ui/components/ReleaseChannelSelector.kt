package com.sourcetx.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourcetx.companion.ui.theme.SourceTxTheme

@Composable
fun ReleaseChannelSelector() {
    val colors = SourceTxTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Release channel",
            fontSize = 11.sp,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Stable Button (Active)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.accent)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Stable",
                color = colors.background,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Experimental Button (Disabled / Preview)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Experimental",
                color = colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
