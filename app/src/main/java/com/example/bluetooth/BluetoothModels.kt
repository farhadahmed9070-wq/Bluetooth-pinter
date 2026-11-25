package com.example.bluetooth

data class BluetoothPrinterDevice(
    val name: String,
    val address: String,
    val isPaired: Boolean,
    val isLikelyPrinter: Boolean
)

sealed interface PrinterConnectionState {
    data object Disconnected : PrinterConnectionState
    data class Connecting(val device: BluetoothPrinterDevice) : PrinterConnectionState
    data class Connected(val device: BluetoothPrinterDevice) : PrinterConnectionState
    data class Error(val message: String, val device: BluetoothPrinterDevice? = null) : PrinterConnectionState
}
