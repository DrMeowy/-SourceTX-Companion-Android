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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourcetx.companion.ui.theme.GreenSuccess
import com.sourcetx.companion.ui.theme.RedDanger
import com.sourcetx.companion.ui.theme.SourceTxTheme
import com.sourcetx.companion.usb.HardwarePinSettings

data class GpioPinOption(val pin: Int, val label: String)

val ALL_GPIO_OPTIONS: List<GpioPinOption> = buildList {
    add(GpioPinOption(-1, "Disabled (-1)"))
    val gpios = listOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 21,
        33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    )
    for (pin in gpios) {
        val extra = when (pin) {
            42 -> " (Default CRSF Single-Wire)"
            43 -> " (ESP32-S3 UART0 TX)"
            44 -> " (ESP32-S3 UART0 RX)"
            45, 46 -> " (Boot Strap)"
            else -> ""
        }
        add(GpioPinOption(pin, "GPIO $pin$extra"))
    }
}

val STATUS_MODE_OPTIONS = listOf(
    "Disabled",
    "Mono Single-Color LED",
    "RGB LED (3-Pin PWM)",
    "Addressable RGB (WS2812 / 1-Pin)"
)

val SOUND_MODE_OPTIONS = listOf(
    "Disabled",
    "Tone Buzzer",
    "DFPlayer Mini (SD Voice)"
)

@Composable
fun ConfigScreen(
    isConnected: Boolean,
    settings: HardwarePinSettings,
    isReading: Boolean,
    isSaving: Boolean,
    successMessage: String?,
    errorMessage: String?,
    logs: List<String>,
    onReadSettings: () -> Unit,
    onSaveSettings: (HardwarePinSettings) -> Unit
) {
    val colors = SourceTxTheme.colors
    var currentSettings by remember(settings) { mutableStateOf(settings) }

    // Real-time conflict validation
    val conflictError = remember(currentSettings) {
        val used = mutableMapOf<Int, String>()
        fun checkCol(pin: Int, name: String): String? {
            if (pin >= 0) {
                if (used.containsKey(pin)) {
                    return "GPIO $pin is assigned to both '${used[pin]}' and '$name'."
                }
                used[pin] = name
            }
            return null
        }
        checkCol(currentSettings.crsfPin, "CRSF UART")
            ?: (if (currentSettings.statusMode == 1) checkCol(currentSettings.statusMonoPin, "Status Mono LED") else null)
            ?: (if (currentSettings.statusMode == 3) checkCol(currentSettings.statusMonoPin, "Status WS2812 LED") else null)
            ?: (if (currentSettings.statusMode == 2) {
                checkCol(currentSettings.statusRedPin, "Status Red LED")
                    ?: checkCol(currentSettings.statusGreenPin, "Status Green LED")
                    ?: checkCol(currentSettings.statusBluePin, "Status Blue LED")
            } else null)
            ?: (if (currentSettings.soundMode != 0) checkCol(currentSettings.soundPin, "Sound Output") else null)
            ?: checkCol(currentSettings.vibrationPin, "Vibration Motor")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Screen Title Header
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
                        .background(colors.configBg)
                        .border(1.dp, colors.configBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Config",
                        tint = colors.configAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Configure Transmitter",
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hardware pinout & NVS storage",
                        color = colors.configAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Button(
                onClick = onReadSettings,
                enabled = isConnected && !isReading && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.configAccent,
                    contentColor = Color(0xFF0A0C10)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isReading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF0A0C10)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reading...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Read from TX", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Connection / Help Notice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                text = if (isConnected) "Transmitter connected via USB OTG. Read current settings or choose new pins below."
                else "Connect transmitter via USB OTG adapter and grant USB permission to read or write hardware pins.",
                color = if (isConnected) colors.textSecondary else colors.textMuted,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Success / Error Alerts
        if (successMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(GreenSuccess.copy(alpha = 0.12f))
                    .border(1.dp, GreenSuccess.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(successMessage, color = GreenSuccess, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (errorMessage != null || conflictError != null) {
            val err = conflictError ?: errorMessage ?: ""
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RedDanger.copy(alpha = 0.12f))
                    .border(1.dp, RedDanger.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(err, color = RedDanger, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // ==========================================
        // CARD 1: CRSF RADIO UART
        // ==========================================
        ConfigSectionCard(
            title = "CRSF Radio UART",
            subtitle = "ExpressLRS / Crossfire module",
            icon = Icons.Default.Wifi,
            accentColor = colors.installAccent
        ) {
            Text("CRSF Single-Wire Pin", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            PinSelectorDropdown(
                selectedPin = currentSettings.crsfPin,
                onPinSelected = { currentSettings = currentSettings.copy(crsfPin = it) }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Half-duplex single-wire serial pin for internal or external RF module (default: GPIO 42).",
                color = colors.textMuted,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==========================================
        // CARD 2: STATUS INDICATOR LED
        // ==========================================
        ConfigSectionCard(
            title = "Status Indicator LED",
            subtitle = "Visual transmitter state indicator",
            icon = Icons.Default.Lightbulb,
            accentColor = colors.configAccent
        ) {
            Text("Indicator Mode", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            OptionSelectorDropdown(
                options = STATUS_MODE_OPTIONS,
                selectedIndex = currentSettings.statusMode,
                onOptionSelected = { currentSettings = currentSettings.copy(statusMode = it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (currentSettings.statusMode) {
                1 -> {
                    Text("Mono LED Pin", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    PinSelectorDropdown(
                        selectedPin = currentSettings.statusMonoPin,
                        onPinSelected = { currentSettings = currentSettings.copy(statusMonoPin = it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                2 -> {
                    Text("RGB Pins (Red / Green / Blue)", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Red", color = Color(0xFFF87171), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            PinSelectorDropdown(
                                selectedPin = currentSettings.statusRedPin,
                                onPinSelected = { currentSettings = currentSettings.copy(statusRedPin = it) }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Green", color = Color(0xFF4ADE80), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            PinSelectorDropdown(
                                selectedPin = currentSettings.statusGreenPin,
                                onPinSelected = { currentSettings = currentSettings.copy(statusGreenPin = it) }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Blue", color = Color(0xFF60A5FA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            PinSelectorDropdown(
                                selectedPin = currentSettings.statusBluePin,
                                onPinSelected = { currentSettings = currentSettings.copy(statusBluePin = it) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                3 -> {
                    Text("WS2812 / NeoPixel Data Pin", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    PinSelectorDropdown(
                        selectedPin = currentSettings.statusMonoPin,
                        onPinSelected = { currentSettings = currentSettings.copy(statusMonoPin = it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (currentSettings.statusMode != 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LED Brightness", color = colors.textSecondary, fontSize = 11.sp)
                    Text("${currentSettings.statusBrightness}%", color = colors.configAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = currentSettings.statusBrightness.toFloat(),
                    onValueChange = { currentSettings = currentSettings.copy(statusBrightness = it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.configAccent,
                        activeTrackColor = colors.configAccent,
                        inactiveTrackColor = colors.border
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==========================================
        // CARD 3: AUDIO & HAPTICS
        // ==========================================
        ConfigSectionCard(
            title = "Audio & Haptic Feedback",
            subtitle = "Buzzer, DFPlayer voice & vibration",
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            accentColor = colors.updateAccent
        ) {
            Text("Sound Output Mode", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            OptionSelectorDropdown(
                options = SOUND_MODE_OPTIONS,
                selectedIndex = currentSettings.soundMode,
                onOptionSelected = { currentSettings = currentSettings.copy(soundMode = it) }
            )

            if (currentSettings.soundMode != 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (currentSettings.soundMode == 1) "Buzzer Pin (PWM)" else "DFPlayer TX/RX Data Pin",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                PinSelectorDropdown(
                    selectedPin = currentSettings.soundPin,
                    onPinSelected = { currentSettings = currentSettings.copy(soundPin = it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Vibration Motor Pin", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            PinSelectorDropdown(
                selectedPin = currentSettings.vibrationPin,
                onPinSelected = { currentSettings = currentSettings.copy(vibrationPin = it) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Save Action Button
        Button(
            onClick = { onSaveSettings(currentSettings) },
            enabled = isConnected && conflictError == null && !isReading && !isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenSuccess,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saving to NVS...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("💾 Save to Transmitter (NVS)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Console Log Deck
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0D1117))
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = "TRANSMITTER NVS CONSOLE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8B949E)
                )
                Spacer(modifier = Modifier.height(4.dp))
                logs.takeLast(6).forEach { logLine ->
                    Text(
                        text = logLine,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = when {
                            logLine.contains("[ERROR]") -> Color(0xFFF85149)
                            logLine.contains("[SUCCESS]") -> Color(0xFF56D364)
                            logLine.contains("[READ]") || logLine.contains("[WRITE]") -> Color(0xFF58A6FF)
                            else -> Color(0xFFC9D1D9)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    val colors = SourceTxTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(title, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = accentColor, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PinSelectorDropdown(
    selectedPin: Int,
    onPinSelected: (Int) -> Unit
) {
    val colors = SourceTxTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val currentOption = ALL_GPIO_OPTIONS.find { it.pin == selectedPin } ?: GpioPinOption(selectedPin, "GPIO $selectedPin")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .clickable { expanded = true }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(currentOption.label, color = colors.textPrimary, fontSize = 11.5.sp, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.textSecondary)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colors.surfaceElevated).border(1.dp, colors.border)
        ) {
            ALL_GPIO_OPTIONS.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label, color = colors.textPrimary, fontSize = 12.sp) },
                    onClick = {
                        onPinSelected(opt.pin)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionSelectorDropdown(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit
) {
    val colors = SourceTxTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val currentText = options.getOrElse(selectedIndex) { options.first() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .clickable { expanded = true }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(currentText, color = colors.textPrimary, fontSize = 11.5.sp)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.textSecondary)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colors.surfaceElevated).border(1.dp, colors.border)
        ) {
            options.forEachIndexed { idx, label ->
                DropdownMenuItem(
                    text = { Text(label, color = colors.textPrimary, fontSize = 12.sp) },
                    onClick = {
                        onOptionSelected(idx)
                        expanded = false
                    }
                )
            }
        }
    }
}
