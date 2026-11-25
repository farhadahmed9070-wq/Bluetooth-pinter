package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale

@Keep
@IgnoreExtraProperties
data class ReceiptLineItem(
    val name: String = "",
    val quantity: Long = 1L,
    val unitPriceCents: Long = 0L
) {
    val lineTotalCents: Long
        get() = quantity * unitPriceCents

    fun toMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(60),
        "quantity" to quantity.coerceIn(1L, 9999L),
        "unitPriceCents" to unitPriceCents.coerceIn(0L, 100_000_000L)
    )
}

@Keep
@IgnoreExtraProperties
data class ReceiptTemplate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val businessName: String = "",
    val businessSubtitle: String = "",
    val headerNote: String = "",
    val footerNote: String = "",
    val paperWidthMm: Long = 58L,
    val currencySymbol: String = "$",
    val showDate: Boolean = true,
    val showLogoHeader: Boolean = true,
    val defaultItems: List<ReceiptLineItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PrintOrder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val orderNumber: String = "",
    val orderType: String = OrderType.RECEIPT.wireValue,
    val title: String = "",
    val businessName: String = "",
    val businessSubtitle: String = "",
    val footerNote: String = "",
    val textContent: String = "",
    val items: List<ReceiptLineItem> = emptyList(),
    val totalCents: Long = 0L,
    val currencySymbol: String = "$",
    val paperWidthMm: Long = 58L,
    val printerName: String = "",
    val printerAddress: String = "",
    val status: String = OrderStatus.QUEUED.wireValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedOrderType(): OrderType = OrderType.fromWire(orderType)
    fun resolvedStatus(): OrderStatus = OrderStatus.fromWire(status)
}

enum class OrderType(val wireValue: String) {
    RECEIPT("receipt"),
    TEXT("text"),
    IMAGE("image");

    companion object {
        fun fromWire(raw: String?): OrderType =
            entries.firstOrNull { it.wireValue == raw } ?: RECEIPT
    }
}

enum class OrderStatus(val wireValue: String) {
    QUEUED("queued"),
    PRINTED("printed"),
    FAILED("failed");

    companion object {
        fun fromWire(raw: String?): OrderStatus =
            entries.firstOrNull { it.wireValue == raw } ?: QUEUED
    }
}

enum class PrintDensity(val level: Int, val label: String) {
    LIGHT(0, "Light"),
    NORMAL(1, "Normal"),
    DARK(2, "Dark");

    companion object {
        fun fromLevel(level: Int): PrintDensity =
            entries.firstOrNull { it.level == level } ?: NORMAL
    }
}

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    ARABIC("ar", "العربية"),
    BENGALI("bn", "বাংলা");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

data class PrinterSettings(
    val paperWidthMm: Int = 58,
    val printDensity: PrintDensity = PrintDensity.NORMAL,
    val autoCutPaper: Boolean = true,
    val defaultCurrency: String = "$",
    val language: AppLanguage = AppLanguage.ENGLISH,
    val lastPrinterName: String = "",
    val lastPrinterAddress: String = ""
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun formatCents(cents: Long, currencySymbol: String): String {
    val absCents = kotlin.math.abs(cents)
    val major = absCents / 100L
    val minor = absCents % 100L
    val sign = if (cents < 0) "-" else ""
    return String.format(Locale.US, "%s%s%d.%02d", sign, currencySymbol, major, minor)
}
