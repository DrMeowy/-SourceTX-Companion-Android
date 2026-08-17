package com.sourcetx.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourcetx.companion.R
import com.sourcetx.companion.ui.components.ActionCard
import com.sourcetx.companion.ui.theme.SourceTxTheme

private data class HomeCard(
    val title: String,
    val subtitle: String,
    val description: String,
    val actionText: String,
    val icon: ImageVector,
    val iconBackground: Color,
    val iconBorder: Color,
    val accent: Color,
    val pill: Color,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    onNavigateToInstall: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onNavigateToConfig: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToRestore: () -> Unit
) {
    val colors = SourceTxTheme.colors
    val cards = listOf(
        HomeCard(
            stringResource(R.string.card_install_title), stringResource(R.string.card_install_subtitle),
            stringResource(R.string.card_install_desc), stringResource(R.string.card_install_action),
            Icons.Default.Build, colors.installBg, colors.installBorder, colors.installAccent,
            colors.installPill, true, onNavigateToInstall
        ),
        HomeCard(
            stringResource(R.string.card_update_title), stringResource(R.string.card_update_subtitle),
            stringResource(R.string.card_update_desc), stringResource(R.string.card_update_action),
            Icons.Default.GetApp, colors.updateBg, colors.updateBorder, colors.updateAccent,
            colors.updatePill, true, onNavigateToUpdate
        ),
        HomeCard(
            stringResource(R.string.card_config_title), stringResource(R.string.card_config_subtitle),
            stringResource(R.string.card_config_desc), stringResource(R.string.card_config_action),
            Icons.Default.Tune, colors.configBg, colors.configBorder, colors.configAccent,
            colors.configPill, true, onNavigateToConfig
        ),
        HomeCard(
            stringResource(R.string.card_backup_title), stringResource(R.string.card_backup_subtitle),
            stringResource(R.string.card_backup_desc), stringResource(R.string.card_backup_action),
            Icons.Default.UploadFile, colors.backupBg, colors.backupBorder, colors.backupAccent,
            colors.backupPill, true, onNavigateToBackup
        ),
        HomeCard(
            stringResource(R.string.card_restore_title), stringResource(R.string.card_restore_subtitle),
            stringResource(R.string.card_restore_desc), stringResource(R.string.card_restore_action),
            Icons.Default.Folder, colors.restoreBg, colors.restoreBorder, colors.restoreAccent,
            colors.restorePill, true, onNavigateToRestore
        )
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        val compact = maxWidth < 700.dp
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                stringResource(R.string.title_home),
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            androidx.compose.material3.Text(
                stringResource(R.string.subtitle_home),
                color = colors.textSecondary,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))

            if (compact) {
                cards.forEachIndexed { index, card ->
                    HomeActionCard(card, Modifier.fillMaxWidth())
                    if (index != cards.lastIndex) Spacer(Modifier.height(10.dp))
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    cards.take(3).forEach { HomeActionCard(it, Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    cards.drop(3).forEach { HomeActionCard(it, Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomeActionCard(card: HomeCard, modifier: Modifier) {
    ActionCard(
        title = card.title,
        subtitle = card.subtitle,
        description = card.description,
        actionText = card.actionText,
        icon = card.icon,
        iconBgColor = card.iconBackground,
        iconBorderColor = card.iconBorder,
        accentColor = card.accent,
        pillBgColor = card.pill,
        enabled = card.enabled,
        badgeText = if (card.enabled) null else "In Development",
        onClick = card.onClick,
        modifier = modifier
    )
}
