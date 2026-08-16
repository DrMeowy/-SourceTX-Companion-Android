package com.sourcetx.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourcetx.companion.R
import com.sourcetx.companion.ui.components.ActionCard
import com.sourcetx.companion.ui.theme.SourceTxTheme

@Composable
fun HomeScreen(
    onNavigateToInstall: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToRestore: () -> Unit
) {
    val colors = SourceTxTheme.colors
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Section Header
        Text(
            text = "SourceTX Surface Companion",
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Visual Hardware Customizer, Flasher & Surface Configurator (v1.98)",
            color = colors.textSecondary,
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CARD 1: INSTALL
            ActionCard(
                title = stringResource(R.string.card_install_title),
                subtitle = stringResource(R.string.card_install_subtitle),
                description = stringResource(R.string.card_install_desc),
                actionText = stringResource(R.string.card_install_action),
                icon = Icons.Default.Build,
                iconBgColor = colors.installBg,
                iconBorderColor = colors.installBorder,
                accentColor = colors.installAccent,
                pillBgColor = colors.installPill,
                onClick = onNavigateToInstall
            )

            // CARD 2: UPDATE
            ActionCard(
                title = stringResource(R.string.card_update_title),
                subtitle = stringResource(R.string.card_update_subtitle),
                description = stringResource(R.string.card_update_desc),
                actionText = stringResource(R.string.card_update_action),
                icon = Icons.Default.Download,
                iconBgColor = colors.updateBg,
                iconBorderColor = colors.updateBorder,
                accentColor = colors.updateAccent,
                pillBgColor = colors.updatePill,
                onClick = onNavigateToUpdate
            )

            // CARD 3: BACKUP
            ActionCard(
                title = stringResource(R.string.card_backup_title),
                subtitle = stringResource(R.string.card_backup_subtitle),
                description = stringResource(R.string.card_backup_desc),
                actionText = stringResource(R.string.card_backup_action),
                icon = Icons.Default.FileUpload,
                iconBgColor = colors.backupBg,
                iconBorderColor = colors.backupBorder,
                accentColor = colors.backupAccent,
                pillBgColor = colors.backupPill,
                onClick = onNavigateToBackup
            )

            // CARD 4: RESTORE
            ActionCard(
                title = stringResource(R.string.card_restore_title),
                subtitle = stringResource(R.string.card_restore_subtitle),
                description = stringResource(R.string.card_restore_desc),
                actionText = stringResource(R.string.card_restore_action),
                icon = Icons.Default.FileDownload,
                iconBgColor = colors.restoreBg,
                iconBorderColor = colors.restoreBorder,
                accentColor = colors.restoreAccent,
                pillBgColor = colors.restorePill,
                onClick = onNavigateToRestore
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
