package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.bluetooth.PrinterConnectionState
import com.example.data.AppLanguage
import com.example.data.PrintDensity
import com.example.data.PrinterSettings
import com.example.ui.i18n.LocalAppStrings

@Composable
fun SettingsScreen(
    settings: PrinterSettings,
    connectionState: PrinterConnectionState,
    userEmail: String?,
    onFindPrinterClick: () -> Unit,
    onDisconnectPrinter: () -> Unit,
    onTestPrint: () -> Unit,
    onPaperWidthChange: (Int) -> Unit,
    onDensityChange: (PrintDensity) -> Unit,
    onAutoCutChange: (Boolean) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currencies = listOf("$", "€", "£", "¥", "৳", "₹")

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Bluetooth Section
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.bluetoothSection, style = MaterialTheme.typography.titleMedium)
                        }

                        val statusText = when (connectionState) {
                            is PrinterConnectionState.Connected -> "${strings.connectedLabel}: ${connectionState.device.name} (${connectionState.device.address})"
                            is PrinterConnectionState.Connecting -> "Connecting to ${connectionState.device.name}..."
                            is PrinterConnectionState.Error -> connectionState.message
                            PrinterConnectionState.Disconnected -> strings.disconnectedLabel
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onFindPrinterClick,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("settings_find_printer")
                            ) {
                                Text(strings.findPrinter)
                            }
                            if (connectionState is PrinterConnectionState.Connected) {
                                OutlinedButton(
                                    onClick = onDisconnectPrinter,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(strings.disconnect)
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.SettingsBluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("System BT")
                            }
                            OutlinedButton(
                                onClick = onTestPrint,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.testPrint)
                            }
                        }
                    }
                }
            }

            // Printer Configuration Section
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(strings.settingsTitle, style = MaterialTheme.typography.titleMedium)

                        Column {
                            Text(strings.paperSizeLabel, style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = settings.paperWidthMm == 58,
                                    onClick = { onPaperWidthChange(58) },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                    modifier = Modifier.testTag("settings_paper_58")
                                ) {
                                    Text("58mm (32 cols)")
                                }
                                SegmentedButton(
                                    selected = settings.paperWidthMm == 80,
                                    onClick = { onPaperWidthChange(80) },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                    modifier = Modifier.testTag("settings_paper_80")
                                ) {
                                    Text("80mm (48 cols)")
                                }
                            }
                        }

                        Column {
                            Text(strings.printDensityLabel, style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                PrintDensity.entries.forEachIndexed { idx, density ->
                                    SegmentedButton(
                                        selected = settings.printDensity == density,
                                        onClick = { onDensityChange(density) },
                                        shape = SegmentedButtonDefaults.itemShape(idx, PrintDensity.entries.size)
                                    ) {
                                        Text(density.label)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.autoCutLabel, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = settings.autoCutPaper,
                                onCheckedChange = onAutoCutChange
                            )
                        }

                        Column {
                            Text(strings.currencyLabel, style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(currencies) { symbol ->
                                    FilterChip(
                                        selected = settings.defaultCurrency == symbol,
                                        onClick = { onCurrencyChange(symbol) },
                                        label = { Text(symbol) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Language Section
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium)
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AppLanguage.entries) { lang ->
                                FilterChip(
                                    selected = settings.language == lang,
                                    onClick = { onLanguageChange(lang) },
                                    label = { Text(lang.displayName) }
                                )
                            }
                        }
                    }
                }
            }

            // Account Section
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(strings.accountSection, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = userEmail ?: "Signed in with Google",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onSignOutClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.signOut)
                        }
                    }
                }
            }
        }
    }
}
