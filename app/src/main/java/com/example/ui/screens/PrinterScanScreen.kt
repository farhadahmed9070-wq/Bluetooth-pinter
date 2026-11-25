package com.example.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bluetooth.BluetoothPermissionHelper
import com.example.bluetooth.BluetoothPrinterDevice
import com.example.bluetooth.PrinterConnectionState
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CobaltContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OnCobaltContainer
import com.example.ui.theme.OnEmeraldContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterScanScreen(
    isBluetoothSupported: Boolean,
    isBluetoothEnabled: Boolean,
    isScanning: Boolean,
    devices: List<BluetoothPrinterDevice>,
    connectionState: PrinterConnectionState,
    onRefreshState: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (BluetoothPrinterDevice) -> Unit,
    onDisconnect: () -> Unit,
    onTestPrint: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    var hasPermissions by remember {
        mutableStateOf(BluetoothPermissionHelper.hasAllBluetoothPermissions(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it } ||
            BluetoothPermissionHelper.hasAllBluetoothPermissions(context)
        hasPermissions = granted
        if (granted) {
            onRefreshState()
            onStartScan()
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshState()
        if (BluetoothPermissionHelper.hasAllBluetoothPermissions(context)) {
            onStartScan()
        }
    }

    LaunchedEffect(Unit) {
        hasPermissions = BluetoothPermissionHelper.hasAllBluetoothPermissions(context)
        onRefreshState()
        if (hasPermissions && isBluetoothEnabled) {
            onStartScan()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.scanPrintersTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("scan_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        modifier = Modifier.testTag("open_system_bluetooth_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsBluetooth,
                            contentDescription = "System Bluetooth settings"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isBluetoothSupported) {
                    item {
                        PermissionOrStateCard(
                            title = "Bluetooth Hardware Unavailable",
                            description = "This device or emulator does not report a Bluetooth adapter. You can still design templates, queue orders, and preview 58mm/80mm receipts.",
                            buttonLabel = "Open System Settings",
                            onAction = {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            }
                        )
                    }
                } else if (!hasPermissions) {
                    item {
                        PermissionOrStateCard(
                            title = "Bluetooth Permissions Required",
                            description = "Grant Bluetooth Scan & Connect permissions to discover nearby 58mm and 80mm thermal printers.",
                            buttonLabel = "Grant Bluetooth Access",
                            onAction = {
                                permissionLauncher.launch(BluetoothPermissionHelper.requiredPermissions())
                            }
                        )
                    }
                } else if (!isBluetoothEnabled) {
                    item {
                        PermissionOrStateCard(
                            title = "Bluetooth Is Turned Off",
                            description = "Enable Bluetooth to scan for paired and nearby thermal receipt printers.",
                            buttonLabel = "Turn On Bluetooth",
                            onAction = {
                                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { if (isScanning) onStopScan() else onStartScan() },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("scan_toggle_button")
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Scanning... Tap to Stop")
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Scan Nearby Printers")
                                }
                            }

                            OutlinedButton(
                                onClick = onRefreshState,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(50.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh paired devices"
                                )
                            }
                        }
                    }
                }

                if (connectionState is PrinterConnectionState.Connected) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = EmeraldContainer.copy(alpha = 0.7f)),
                            border = BorderStroke(1.dp, OnEmeraldContainer.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.BluetoothConnected,
                                        contentDescription = null,
                                        tint = OnEmeraldContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Active: ${connectionState.device.name}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = OnEmeraldContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = onTestPrint,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.testPrint)
                                    }
                                    OutlinedButton(
                                        onClick = onDisconnect,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(strings.disconnect)
                                    }
                                }
                            }
                        }
                    }
                }

                if (devices.isEmpty() && hasPermissions && isBluetoothEnabled) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bluetooth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isScanning) "Searching for nearby printers..." else "No printers found yet",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Power on your 58mm or 80mm thermal printer, or pair it in Android Bluetooth Settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(devices, key = { it.address }) { device ->
                    val isThisConnected = (connectionState as? PrinterConnectionState.Connected)?.device?.address == device.address
                    val isThisConnecting = (connectionState as? PrinterConnectionState.Connecting)?.device?.address == device.address
                    PrinterDeviceRow(
                        device = device,
                        isConnected = isThisConnected,
                        isConnecting = isThisConnecting,
                        onConnect = { onConnectDevice(device) },
                        onDisconnect = onDisconnect
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionOrStateCard(
    title: String,
    description: String,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("bluetooth_action_button")
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun PrinterDeviceRow(
    device: BluetoothPrinterDevice,
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (device.isLikelyPrinter) CobaltContainer else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (device.isLikelyPrinter) Icons.Rounded.Print else Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = if (device.isLikelyPrinter) OnCobaltContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (device.isPaired) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CobaltContainer
                        ) {
                            Text(
                                text = "Paired",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnCobaltContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Connect")
                }
            }
        }
    }
}
