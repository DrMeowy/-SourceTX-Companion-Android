package com.sourcetx.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Tune
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Section Header
        Text(
            text = "SourceTX Companion",
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Install, update, and back up your SourceTX transmitter",
            color = colors.textSecondary,
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ==========================================
        // ROW 1: 3 CARDS AT TOP (Install, Update, Configure)
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                onClick = onNavigateToInstall,
                modifier = Modifier.weight(1f)
            )

            // CARD 2: UPDATE
            ActionCard(
                title = stringResource(R.string.card_update_title),
                subtitle = stringResource(R.string.card_update_subtitle),
                description = stringResource(R.string.card_update_desc),
                actionText = stringResource(R.string.card_update_action),
                icon = Icons.Default.GetApp,
                iconBgColor = colors.updateBg,
                iconBorderColor = colors.updateBorder,
                accentColor = colors.updateAccent,
                pillBgColor = colors.updatePill,
                onClick = onNavigateToUpdate,
                modifier = Modifier.weight(1f)
            )

            // CARD 3: CONFIGURE (IN DEVELOPMENT)
            ActionCard(
                title = stringResource(R.string.card_config_title),
                subtitle = stringResource(R.string.card_config_subtitle),
                description = stringResource(R.string.card_config_desc),
                actionText = stringResource(R.string.card_config_action),
                icon = Icons.Default.Tune,
                iconBgColor = colors.configBg,
                iconBorderColor = colors.configBorder,
                accentColor = colors.configAccent,
                pillBgColor = colors.configPill,
                enabled = false,
                badgeText = "In Development",
                onClick = {},
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ==========================================
        // ROW 2: 2 CARDS AT BOTTOM (Backup, Restore)
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // CARD 4: BACKUP
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
                onClick = onNavigateToBackup,
                modifier = Modifier.weight(1f)
            )

            // CARD 5: RESTORE
            ActionCard(
                title = stringResource(R.string.card_restore_title),
                subtitle = stringResource(R.string.card_restore_subtitle),
                description = stringResource(R.string.card_restore_desc),
                actionText = stringResource(R.string.card_restore_action),
                icon = Icons.Default.Folder,
                iconBgColor = colors.restoreBg,
                iconBorderColor = colors.restoreBorder,
                accentColor = colors.restoreAccent,
                pillBgColor = colors.restorePill,
                onClick = onNavigateToRestore,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
