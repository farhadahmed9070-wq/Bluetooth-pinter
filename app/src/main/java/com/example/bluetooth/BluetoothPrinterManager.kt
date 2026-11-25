package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

private const val BT_TAG = "BluetoothPrinterMgr"
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class BluetoothPrinterManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _isBluetoothSupported = MutableStateFlow(bluetoothAdapter != null)
    val isBluetoothSupported: StateFlow<Boolean> = _isBluetoothSupported.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothPrinterDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothPrinterDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Disconnected)
    val connectionState: StateFlow<PrinterConnectionState> = _connectionState.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var receiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && BluetoothPermissionHelper.hasConnectPermission(appContext)) {
                        upsertDevice(device.toPrinterDevice(isPairedOverride = device.bondState == BluetoothDevice.BOND_BONDED))
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                    if (state != BluetoothAdapter.STATE_ON) {
                        _isScanning.value = false
                        closeSocketSilently()
                        _connectionState.value = PrinterConnectionState.Disconnected
                    }
                }
            }
        }
    }

    init {
        registerReceiverIfNeeded()
    }

    fun refreshState() {
        _isBluetoothSupported.value = bluetoothAdapter != null
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
        loadPairedDevices()
    }

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        val adapter = bluetoothAdapter ?: return
        if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) return
        val bonded = try {
            adapter.bondedDevices?.map { it.toPrinterDevice(isPairedOverride = true) } ?: emptyList()
        } catch (e: SecurityException) {
            Log.w(BT_TAG, "Missing permission reading bonded devices: ${e.message}")
            emptyList()
        }
        val existingNearby = _discoveredDevices.value.filter { !it.isPaired }
        val merged = (bonded + existingNearby)
            .distinctBy { it.address }
            .sortedWith(compareByDescending<BluetoothPrinterDevice> { it.isPaired }.thenByDescending { it.isLikelyPrinter }.thenBy { it.name })
        _discoveredDevices.value = merged
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Result<Unit> {
        registerReceiverIfNeeded()
        val adapter = bluetoothAdapter
            ?: return Result.failure(IllegalStateException("Bluetooth hardware is unavailable on this device."))
        _isBluetoothEnabled.value = adapter.isEnabled
        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth is turned off. Please enable Bluetooth first."))
        }
        if (!BluetoothPermissionHelper.hasAllBluetoothPermissions(appContext)) {
            return Result.failure(SecurityException("Bluetooth permission is required to scan nearby printers."))
        }

        loadPairedDevices()
        return try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            val started = adapter.startDiscovery()
            _isScanning.value = started
            if (started) Result.success(Unit)
            else Result.failure(IllegalStateException("Could not start Bluetooth discovery."))
        } catch (e: SecurityException) {
            _isScanning.value = false
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!BluetoothPermissionHelper.hasScanPermission(appContext)) return
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Log.w(BT_TAG, "Failed stopping discovery: ${e.message}")
        } finally {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothPrinterDevice): Result<BluetoothPrinterDevice> = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth unavailable"))
        if (!adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is turned off"))
        }
        if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
            return@withContext Result.failure(SecurityException("Bluetooth Connect permission is required"))
        }

        _connectionState.value = PrinterConnectionState.Connecting(device)
        closeSocketSilently()

        try {
            if (BluetoothPermissionHelper.hasScanPermission(appContext) && adapter.isDiscovering) {
                adapter.cancelDiscovery()
                _isScanning.value = false
            }

            val remoteDevice = adapter.getRemoteDevice(device.address)
            var activeSocket: BluetoothSocket? = null

            try {
                activeSocket = remoteDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                activeSocket.connect()
            } catch (primaryEx: IOException) {
                Log.w(BT_TAG, "Secure RFCOMM connect failed, trying insecure fallback: ${primaryEx.message}")
                try { activeSocket?.close() } catch (_: IOException) {}
                activeSocket = remoteDevice.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                activeSocket.connect()
            }

            socket = activeSocket
            outputStream = activeSocket?.outputStream
            _connectionState.value = PrinterConnectionState.Connected(device)
            Result.success(device)
        } catch (e: Exception) {
            Log.e(BT_TAG, "Failed connecting to ${device.name} (${device.address})", e)
            closeSocketSilently()
            val message = e.localizedMessage ?: "Could not connect to ${device.name}"
            _connectionState.value = PrinterConnectionState.Error(message, device)
            Result.failure(e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        closeSocketSilently()
        _connectionState.value = PrinterConnectionState.Disconnected
    }

    suspend fun sendBytes(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val stream = outputStream
        val currentSocket = socket
        if (stream == null || currentSocket == null || !currentSocket.isConnected) {
            return@withContext Result.failure(IllegalStateException("No Bluetooth printer connected"))
        }

        return@withContext try {
            val chunkSize = 1024
            var offset = 0
            while (offset < data.size) {
                val len = minOf(chunkSize, data.size - offset)
                stream.write(data, offset, len)
                stream.flush()
                offset += len
                if (offset < data.size) {
                    delay(10)
                }
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(BT_TAG, "Bluetooth print write failed", e)
            closeSocketSilently()
            _connectionState.value = PrinterConnectionState.Error("Printer connection lost: ${e.message}")
            Result.failure(e)
        }
    }

    fun release() {
        stopDiscovery()
        closeSocketSilently()
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(discoveryReceiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            appContext,
            discoveryReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun closeSocketSilently() {
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        outputStream = null
        socket = null
    }

    private fun upsertDevice(device: BluetoothPrinterDevice) {
        val current = _discoveredDevices.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.address == device.address }
        if (existingIdx >= 0) {
            current[existingIdx] = device
        } else {
            current.add(device)
        }
        _discoveredDevices.value = current.sortedWith(
            compareByDescending<BluetoothPrinterDevice> { it.isPaired }
                .thenByDescending { it.isLikelyPrinter }
                .thenBy { it.name }
        )
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toPrinterDevice(isPairedOverride: Boolean): BluetoothPrinterDevice {
        val rawName = try {
            name ?: "Bluetooth Device"
        } catch (_: SecurityException) {
            "Bluetooth Device"
        }
        val majorClass = try {
            bluetoothClass?.majorDeviceClass
        } catch (_: SecurityException) {
            null
        }
        val lower = rawName.lowercase()
        val printerKeywords = listOf(
            "print", "mtp", "rpp", "pos", "xp-", "gp-", "bt-", "pt-", "zj",
            "thermal", "rongta", "xprinter", "munbyn", "goojprt", "epson",
            "star", "zebra", "bixolon", "sunmi", "hprt", "issyzone"
        )
        val likelyPrinter = majorClass == BluetoothClass.Device.Major.IMAGING ||
            printerKeywords.any { lower.contains(it) }
        return BluetoothPrinterDevice(
            name = rawName,
            address = address,
            isPaired = isPairedOverride,
            isLikelyPrinter = likelyPrinter
        )
    }
}
