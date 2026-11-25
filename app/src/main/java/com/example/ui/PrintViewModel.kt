package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothPrinterDevice
import com.example.bluetooth.BluetoothPrinterManager
import com.example.bluetooth.EscPosEncoder
import com.example.bluetooth.PrinterConnectionState
import com.example.bluetooth.TextAlignMode
import com.example.bluetooth.TextSizeMode
import com.example.data.AppLanguage
import com.example.data.OrderStatus
import com.example.data.OrderType
import com.example.data.PrintDensity
import com.example.data.PrintOrder
import com.example.data.PrinterRepository
import com.example.data.PrinterSettings
import com.example.data.ReceiptLineItem
import com.example.data.ReceiptTemplate
import com.example.data.SettingsRepository
import com.example.data.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PrintViewModel(
    private val repository: PrinterRepository,
    private val settingsRepository: SettingsRepository,
    private val bluetoothManager: BluetoothPrinterManager,
    private val currentUserId: String
) : ViewModel() {

    val templatesState: StateFlow<UiState<List<ReceiptTemplate>>> =
        repository.observeTemplates(currentUserId)
            .map<List<ReceiptTemplate>, UiState<List<ReceiptTemplate>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing templates", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load receipt templates"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val ordersState: StateFlow<UiState<List<PrintOrder>>> =
        repository.observeOrders(currentUserId)
            .map<List<PrintOrder>, UiState<List<PrintOrder>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing orders", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load orders"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val settings: StateFlow<PrinterSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PrinterSettings()
        )

    val isBluetoothSupported: StateFlow<Boolean> = bluetoothManager.isBluetoothSupported
    val isBluetoothEnabled: StateFlow<Boolean> = bluetoothManager.isBluetoothEnabled
    val isScanning: StateFlow<Boolean> = bluetoothManager.isScanning
    val discoveredDevices: StateFlow<List<BluetoothPrinterDevice>> = bluetoothManager.discoveredDevices
    val connectionState: StateFlow<PrinterConnectionState> = bluetoothManager.connectionState

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun refreshBluetoothState() {
        bluetoothManager.refreshState()
    }

    fun startPrinterScan() {
        val result = bluetoothManager.startDiscovery()
        if (result.isFailure) {
            _messages.tryEmit(result.exceptionOrNull()?.message ?: "Unable to scan for printers")
        }
    }

    fun stopPrinterScan() {
        bluetoothManager.stopDiscovery()
    }

    fun connectPrinter(device: BluetoothPrinterDevice) {
        viewModelScope.launch {
            val result = bluetoothManager.connect(device)
            if (result.isSuccess) {
                settingsRepository.saveLastConnectedPrinter(device.name, device.address)
                _messages.tryEmit("Connected to ${device.name}")
            } else {
                _messages.tryEmit(result.exceptionOrNull()?.message ?: "Connection failed")
            }
        }
    }

    fun disconnectPrinter() {
        viewModelScope.launch {
            bluetoothManager.disconnect()
            _messages.tryEmit("Printer disconnected")
        }
    }

    fun printTestPage() {
        val connected = connectionState.value as? PrinterConnectionState.Connected
        if (connected == null) {
            _messages.tryEmit("Connect a Bluetooth printer first to run a test print")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val payload = EscPosEncoder.encodeTestPage(connected.device.name, settings.value)
                val result = bluetoothManager.sendBytes(payload)
                if (result.isSuccess) {
                    _messages.tryEmit("Test page sent to ${connected.device.name}")
                } else {
                    _messages.tryEmit(result.exceptionOrNull()?.message ?: "Failed to print test page")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun saveTemplate(template: ReceiptTemplate, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val result = repository.saveTemplate(template)
                if (result.isSuccess) {
                    _messages.tryEmit("Template \"${template.name.trim()}\" saved")
                    onSaved()
                } else {
                    _messages.tryEmit(result.exceptionOrNull()?.message ?: "Failed to save template")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            val result = repository.deleteTemplate(templateId)
            if (result.isSuccess) {
                _messages.tryEmit("Template deleted")
            } else {
                _messages.tryEmit(result.exceptionOrNull()?.message ?: "Failed to delete template")
            }
        }
    }

    fun seedStarterTemplates() {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val result = repository.seedStarterTemplates()
                if (result.isSuccess) {
                    _messages.tryEmit("Starter templates added")
                } else {
                    _messages.tryEmit(result.exceptionOrNull()?.message ?: "Could not add templates")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun submitReceiptOrder(
        businessName: String,
        businessSubtitle: String,
        headerNote: String,
        footerNote: String,
        items: List<ReceiptLineItem>,
        paperWidthMm: Int,
        currencySymbol: String,
        showDate: Boolean,
        printImmediately: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val cleanItems = items.filter { it.name.isNotBlank() }
                val totalCents = cleanItems.sumOf { it.lineTotalCents }
                val connected = connectionState.value as? PrinterConnectionState.Connected
                val orderNumber = "ORD-${(System.currentTimeMillis() % 90000L) + 10000L}"

                var status = OrderStatus.QUEUED
                var printerName = connected?.device?.name ?: "Queued (Offline)"
                var printerAddress = connected?.device?.address ?: ""

                val draftOrder = PrintOrder(
                    orderNumber = orderNumber,
                    orderType = OrderType.RECEIPT.wireValue,
                    title = businessName.trim().ifEmpty { "Receipt $orderNumber" },
                    businessName = businessName.trim(),
                    businessSubtitle = businessSubtitle.trim(),
                    footerNote = footerNote.trim(),
                    textContent = headerNote.trim(),
                    items = cleanItems,
                    totalCents = totalCents,
                    currencySymbol = currencySymbol,
                    paperWidthMm = paperWidthMm.toLong(),
                    printerName = printerName,
                    printerAddress = printerAddress,
                    status = OrderStatus.QUEUED.wireValue
                )

                if (printImmediately) {
                    if (connected != null) {
                        val bytes = EscPosEncoder.encodeReceipt(
                            order = draftOrder,
                            settings = settings.value.copy(paperWidthMm = paperWidthMm),
                            showDate = showDate,
                            headerNote = headerNote
                        )
                        val printRes = bluetoothManager.sendBytes(bytes)
                        if (printRes.isSuccess) {
                            status = OrderStatus.PRINTED
                            _messages.tryEmit("Receipt $orderNumber printed on ${connected.device.name}")
                        } else {
                            status = OrderStatus.FAILED
                            _messages.tryEmit(printRes.exceptionOrNull()?.message ?: "Print failed; order saved")
                        }
                    } else {
                        _messages.tryEmit("No printer connected — saved $orderNumber as Queued")
                    }
                } else {
                    _messages.tryEmit("Order $orderNumber saved to Orders")
                }

                val savedOrder = draftOrder.copy(
                    printerName = printerName,
                    printerAddress = printerAddress,
                    status = status.wireValue
                )
                val saveRes = repository.createOrder(savedOrder)
                if (saveRes.isFailure) {
                    _messages.tryEmit(saveRes.exceptionOrNull()?.message ?: "Failed saving order history")
                } else {
                    onComplete()
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun printTextJob(
        text: String,
        align: TextAlignMode,
        size: TextSizeMode,
        bold: Boolean,
        feedLines: Int,
        onComplete: () -> Unit = {}
    ) {
        if (text.isBlank()) {
            _messages.tryEmit("Please enter text to print")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val activeSettings = settings.value
                val connected = connectionState.value as? PrinterConnectionState.Connected
                val orderNumber = "TXT-${(System.currentTimeMillis() % 90000L) + 10000L}"

                var status = OrderStatus.QUEUED
                val printerName = connected?.device?.name ?: "Queued (Offline)"
                val printerAddress = connected?.device?.address ?: ""

                if (connected != null) {
                    val bytes = EscPosEncoder.encodeTextJob(
                        text = text,
                        align = align,
                        size = size,
                        bold = bold,
                        feedLinesCount = feedLines,
                        settings = activeSettings
                    )
                    val res = bluetoothManager.sendBytes(bytes)
                    if (res.isSuccess) {
                        status = OrderStatus.PRINTED
                        _messages.tryEmit("Text printed on ${connected.device.name}")
                    } else {
                        status = OrderStatus.FAILED
                        _messages.tryEmit(res.exceptionOrNull()?.message ?: "Failed to print text")
                    }
                } else {
                    _messages.tryEmit("No printer connected — text job saved as Queued")
                }

                val firstLine = text.trim().lineSequence().firstOrNull()?.take(48) ?: "Text Print"
                repository.createOrder(
                    PrintOrder(
                        orderNumber = orderNumber,
                        orderType = OrderType.TEXT.wireValue,
                        title = firstLine,
                        textContent = text.trim().take(2000),
                        totalCents = 0L,
                        currencySymbol = activeSettings.defaultCurrency,
                        paperWidthMm = activeSettings.paperWidthMm.toLong(),
                        printerName = printerName,
                        printerAddress = printerAddress,
                        status = status.wireValue
                    )
                )
                onComplete()
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun printImageJob(
        bitmap: Bitmap,
        label: String,
        threshold: Int,
        useDithering: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val activeSettings = settings.value
                val connected = connectionState.value as? PrinterConnectionState.Connected
                val orderNumber = "IMG-${(System.currentTimeMillis() % 90000L) + 10000L}"

                var status = OrderStatus.QUEUED
                val printerName = connected?.device?.name ?: "Queued (Offline)"
                val printerAddress = connected?.device?.address ?: ""

                if (connected != null) {
                    val bytes = EscPosEncoder.encodeBitmap(
                        source = bitmap,
                        settings = activeSettings,
                        threshold = threshold,
                        useDithering = useDithering
                    )
                    val res = bluetoothManager.sendBytes(bytes)
                    if (res.isSuccess) {
                        status = OrderStatus.PRINTED
                        _messages.tryEmit("Image printed on ${connected.device.name}")
                    } else {
                        status = OrderStatus.FAILED
                        _messages.tryEmit(res.exceptionOrNull()?.message ?: "Failed to print image")
                    }
                } else {
                    _messages.tryEmit("No printer connected — image job logged as Queued")
                }

                repository.createOrder(
                    PrintOrder(
                        orderNumber = orderNumber,
                        orderType = OrderType.IMAGE.wireValue,
                        title = label.ifBlank { "Image Print" }.take(80),
                        textContent = "Raster image (${activeSettings.paperWidthMm}mm, ${bitmap.width}x${bitmap.height}px)",
                        totalCents = 0L,
                        currencySymbol = activeSettings.defaultCurrency,
                        paperWidthMm = activeSettings.paperWidthMm.toLong(),
                        printerName = printerName,
                        printerAddress = printerAddress,
                        status = status.wireValue
                    )
                )
                onComplete()
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun reprintOrder(order: PrintOrder) {
        val connected = connectionState.value as? PrinterConnectionState.Connected
        if (connected == null) {
            _messages.tryEmit("Connect a Bluetooth printer first to reprint ${order.orderNumber}")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val activeSettings = settings.value
                val bytes = when (order.resolvedOrderType()) {
                    OrderType.TEXT -> EscPosEncoder.encodeTextJob(
                        text = order.textContent.ifBlank { order.title },
                        align = TextAlignMode.LEFT,
                        size = TextSizeMode.NORMAL,
                        bold = false,
                        feedLinesCount = 3,
                        settings = activeSettings.copy(paperWidthMm = order.paperWidthMm.toInt())
                    )
                    OrderType.IMAGE -> EscPosEncoder.encodeTextJob(
                        text = "[Image Reprint Placeholder]\n${order.title}\n${order.textContent}",
                        align = TextAlignMode.CENTER,
                        size = TextSizeMode.NORMAL,
                        bold = false,
                        feedLinesCount = 3,
                        settings = activeSettings.copy(paperWidthMm = order.paperWidthMm.toInt())
                    )
                    OrderType.RECEIPT -> EscPosEncoder.encodeReceipt(
                        order = order,
                        settings = activeSettings.copy(paperWidthMm = order.paperWidthMm.toInt()),
                        showDate = true,
                        headerNote = order.textContent
                    )
                }

                val printRes = bluetoothManager.sendBytes(bytes)
                val wasAlreadyPrinted = order.resolvedStatus() == OrderStatus.PRINTED
                val newStatus = if (printRes.isSuccess) OrderStatus.PRINTED else OrderStatus.FAILED
                repository.updateOrderPrintStatus(
                    orderId = order.id,
                    newStatus = newStatus,
                    wasAlreadyPrinted = wasAlreadyPrinted,
                    printerName = connected.device.name,
                    printerAddress = connected.device.address
                )
                if (printRes.isSuccess) {
                    _messages.tryEmit("Reprinted ${order.orderNumber} on ${connected.device.name}")
                } else {
                    _messages.tryEmit(printRes.exceptionOrNull()?.message ?: "Reprint failed")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteOrder(orderId: String) {
        viewModelScope.launch {
            val res = repository.deleteOrder(orderId)
            if (res.isSuccess) {
                _messages.tryEmit("Order removed")
            } else {
                _messages.tryEmit(res.exceptionOrNull()?.message ?: "Failed to remove order")
            }
        }
    }

    fun setPaperWidth(widthMm: Int) {
        viewModelScope.launch { settingsRepository.setPaperWidth(widthMm) }
    }

    fun setPrintDensity(density: PrintDensity) {
        viewModelScope.launch { settingsRepository.setPrintDensity(density) }
    }

    fun setAutoCutPaper(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCutPaper(enabled) }
    }

    fun setDefaultCurrency(symbol: String) {
        viewModelScope.launch { settingsRepository.setDefaultCurrency(symbol) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.release()
    }

    private companion object {
        const val TAG = "PrintVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
