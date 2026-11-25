package com.example.bluetooth

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.example.data.PrintDensity
import com.example.data.PrintOrder
import com.example.data.PrinterSettings
import com.example.data.formatCents
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class TextAlignMode(val escPosCode: Int) {
    LEFT(0),
    CENTER(1),
    RIGHT(2)
}

enum class TextSizeMode(val gsCommandByte: Int) {
    NORMAL(0x00),
    DOUBLE_HEIGHT(0x01),
    DOUBLE_WIDTH(0x10),
    LARGE(0x11)
}

object EscPosEncoder {

    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    fun columnsForPaper(paperWidthMm: Int): Int = if (paperWidthMm == 80) 48 else 32

    fun pixelWidthForPaper(paperWidthMm: Int): Int = if (paperWidthMm == 80) 576 else 384

    private fun densityCommand(density: PrintDensity): ByteArray {
        // ESC 7 n1 n2 n3: heating dots, heating time (higher = darker), interval
        val heatingTime = when (density) {
            PrintDensity.LIGHT -> 60
            PrintDensity.NORMAL -> 100
            PrintDensity.DARK -> 150
        }
        return byteArrayOf(0x1B, 0x37, 7, heatingTime.toByte(), 2)
    }

    private fun alignCommand(align: TextAlignMode): ByteArray =
        byteArrayOf(0x1B, 0x61, align.escPosCode.toByte())

    private fun boldCommand(bold: Boolean): ByteArray =
        byteArrayOf(0x1B, 0x45, if (bold) 1 else 0)

    private fun sizeCommand(size: TextSizeMode): ByteArray =
        byteArrayOf(0x1D, 0x21, size.gsCommandByte.toByte())

    private fun feedLines(lines: Int): ByteArray =
        byteArrayOf(0x1B, 0x64, lines.coerceIn(1, 10).toByte())

    private fun encodeAsciiSafe(text: String): ByteArray {
        // Normalize common symbols to CP437 / ASCII-safe thermal printer bytes
        val normalized = text
            .replace("•", "-")
            .replace("—", "-")
            .replace("–", "-")
            .replace("€", "EUR ")
            .replace("£", "GBP ")
            .replace("¥", "JPY ")
            .replace("৳", "BDT ")
            .replace("₹", "INR ")
        return normalized.toByteArray(Charsets.UTF_8)
    }

    private fun formatTwoColumns(left: String, right: String, totalColumns: Int): String {
        val cleanLeft = left.trim()
        val cleanRight = right.trim()
        if (cleanLeft.length + cleanRight.length + 1 <= totalColumns) {
            val spaces = totalColumns - cleanLeft.length - cleanRight.length
            return cleanLeft + " ".repeat(spaces) + cleanRight
        }
        val maxLeft = (totalColumns - cleanRight.length - 1).coerceAtLeast(1)
        val truncatedLeft = if (cleanLeft.length > maxLeft) {
            cleanLeft.take((maxLeft - 1).coerceAtLeast(1)) + "."
        } else {
            cleanLeft
        }
        val spaces = (totalColumns - truncatedLeft.length - cleanRight.length).coerceAtLeast(1)
        return truncatedLeft + " ".repeat(spaces) + cleanRight
    }

    fun encodeTextJob(
        text: String,
        align: TextAlignMode,
        size: TextSizeMode,
        bold: Boolean,
        feedLinesCount: Int,
        settings: PrinterSettings
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ESC_INIT)
        out.write(densityCommand(settings.printDensity))
        out.write(alignCommand(align))
        out.write(boldCommand(bold))
        out.write(sizeCommand(size))
        out.write(encodeAsciiSafe(text.trimEnd() + "\n"))
        out.write(sizeCommand(TextSizeMode.NORMAL))
        out.write(boldCommand(false))
        out.write(alignCommand(TextAlignMode.LEFT))
        out.write(feedLines(feedLinesCount.coerceIn(1, 8)))
        if (settings.autoCutPaper) {
            out.write(CUT_PARTIAL)
        }
        return out.toByteArray()
    }

    fun encodeReceipt(
        order: PrintOrder,
        settings: PrinterSettings,
        showDate: Boolean = true,
        headerNote: String = ""
    ): ByteArray {
        val paperMm = if (order.paperWidthMm == 80L) 80 else 58
        val cols = columnsForPaper(paperMm)
        val divider = "-".repeat(cols)
        val out = ByteArrayOutputStream()

        out.write(ESC_INIT)
        out.write(densityCommand(settings.printDensity))

        // Header
        out.write(alignCommand(TextAlignMode.CENTER))
        out.write(boldCommand(true))
        out.write(sizeCommand(TextSizeMode.DOUBLE_HEIGHT))
        val biz = order.businessName.ifBlank { order.title.ifBlank { "Bluetooth Print" } }
        out.write(encodeAsciiSafe("$biz\n"))

        out.write(sizeCommand(TextSizeMode.NORMAL))
        out.write(boldCommand(false))
        if (order.businessSubtitle.isNotBlank()) {
            out.write(encodeAsciiSafe("${order.businessSubtitle}\n"))
        }
        if (headerNote.isNotBlank()) {
            out.write(encodeAsciiSafe("$headerNote\n"))
        }
        out.write(encodeAsciiSafe("$divider\n"))

        // Order metadata
        out.write(alignCommand(TextAlignMode.LEFT))
        val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(
            order.createdAt?.toDate() ?: Date()
        )
        out.write(encodeAsciiSafe(formatTwoColumns("Order: ${order.orderNumber}", "${paperMm}mm", cols) + "\n"))
        if (showDate) {
            out.write(encodeAsciiSafe("Date: $dateText\n"))
        }
        out.write(encodeAsciiSafe("$divider\n"))

        // Line items
        if (order.items.isNotEmpty()) {
            out.write(boldCommand(true))
            out.write(encodeAsciiSafe(formatTwoColumns("ITEM", "AMOUNT", cols) + "\n"))
            out.write(boldCommand(false))
            for (item in order.items) {
                val qtyPrefix = "${item.quantity}x "
                val priceStr = formatCents(item.lineTotalCents, order.currencySymbol)
                out.write(encodeAsciiSafe(formatTwoColumns("$qtyPrefix${item.name}", priceStr, cols) + "\n"))
            }
            out.write(encodeAsciiSafe("$divider\n"))
        } else if (order.textContent.isNotBlank()) {
            out.write(encodeAsciiSafe("${order.textContent}\n"))
            out.write(encodeAsciiSafe("$divider\n"))
        }

        // Total
        val totalAmount = formatCents(order.totalCents, order.currencySymbol)
        out.write(boldCommand(true))
        out.write(sizeCommand(TextSizeMode.DOUBLE_HEIGHT))
        out.write(encodeAsciiSafe(formatTwoColumns("TOTAL", totalAmount, cols) + "\n"))
        out.write(sizeCommand(TextSizeMode.NORMAL))
        out.write(boldCommand(false))

        // Footer
        if (order.footerNote.isNotBlank()) {
            out.write(encodeAsciiSafe("$divider\n"))
            out.write(alignCommand(TextAlignMode.CENTER))
            out.write(encodeAsciiSafe("${order.footerNote}\n"))
        }

        out.write(alignCommand(TextAlignMode.LEFT))
        out.write(feedLines(4))
        if (settings.autoCutPaper) {
            out.write(CUT_PARTIAL)
        }
        return out.toByteArray()
    }

    fun encodeTestPage(printerName: String, settings: PrinterSettings): ByteArray {
        val cols = columnsForPaper(settings.paperWidthMm)
        val divider = "=".repeat(cols)
        val out = ByteArrayOutputStream()
        out.write(ESC_INIT)
        out.write(densityCommand(settings.printDensity))
        out.write(alignCommand(TextAlignMode.CENTER))
        out.write(boldCommand(true))
        out.write(sizeCommand(TextSizeMode.DOUBLE_HEIGHT))
        out.write(encodeAsciiSafe("BLUETOOTH PRINT\n"))
        out.write(sizeCommand(TextSizeMode.NORMAL))
        out.write(boldCommand(false))
        out.write(encodeAsciiSafe("Self-Test Receipt\n"))
        out.write(encodeAsciiSafe("$divider\n"))
        out.write(alignCommand(TextAlignMode.LEFT))
        out.write(encodeAsciiSafe(formatTwoColumns("Printer:", printerName.take(18), cols) + "\n"))
        out.write(encodeAsciiSafe(formatTwoColumns("Paper Width:", "${settings.paperWidthMm}mm", cols) + "\n"))
        out.write(encodeAsciiSafe(formatTwoColumns("Density:", settings.printDensity.label, cols) + "\n"))
        out.write(encodeAsciiSafe(formatTwoColumns("Columns:", "$cols chars", cols) + "\n"))
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        out.write(encodeAsciiSafe("Time: $now\n"))
        out.write(encodeAsciiSafe("$divider\n"))
        out.write(alignCommand(TextAlignMode.CENTER))
        out.write(encodeAsciiSafe("Connection OK!\n"))
        out.write(feedLines(4))
        if (settings.autoCutPaper) {
            out.write(CUT_PARTIAL)
        }
        return out.toByteArray()
    }

    fun prepareMonochromeBitmap(
        source: Bitmap,
        paperWidthMm: Int,
        threshold: Int = 128,
        useDithering: Boolean = true
    ): Bitmap {
        val maxDots = pixelWidthForPaper(paperWidthMm)
        val targetWidthRaw = minOf(source.width, maxDots).coerceAtLeast(8)
        // Round down to multiple of 8 for clean GS v 0 byte alignment
        val targetWidth = (targetWidthRaw / 8) * 8
        val aspect = source.height.toFloat() / source.width.toFloat().coerceAtLeast(1f)
        val targetHeight = (targetWidth * aspect).roundToInt().coerceIn(8, 2400)

        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        val grayBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayBitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        if (scaled !== source) scaled.recycle()

        val pixels = IntArray(targetWidth * targetHeight)
        grayBitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        val lum = FloatArray(targetWidth * targetHeight)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val outPixels = IntArray(targetWidth * targetHeight)
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val idx = y * targetWidth + x
                val oldVal = lum[idx]
                val isBlack = oldVal < threshold
                val newVal = if (isBlack) 0f else 255f
                outPixels[idx] = if (isBlack) Color.BLACK else Color.WHITE

                if (useDithering) {
                    val error = oldVal - newVal
                    if (x + 1 < targetWidth) lum[idx + 1] += error * (7f / 16f)
                    if (y + 1 < targetHeight) {
                        if (x - 1 >= 0) lum[idx + targetWidth - 1] += error * (3f / 16f)
                        lum[idx + targetWidth] += error * (5f / 16f)
                        if (x + 1 < targetWidth) lum[idx + targetWidth + 1] += error * (1f / 16f)
                    }
                }
            }
        }

        val mono = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        mono.setPixels(outPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        grayBitmap.recycle()
        return mono
    }

    fun encodeBitmap(
        source: Bitmap,
        settings: PrinterSettings,
        threshold: Int = 128,
        useDithering: Boolean = true
    ): ByteArray {
        val mono = prepareMonochromeBitmap(source, settings.paperWidthMm, threshold, useDithering)
        val width = mono.width
        val height = mono.height
        val widthBytes = width / 8

        val out = ByteArrayOutputStream()
        out.write(ESC_INIT)
        out.write(densityCommand(settings.printDensity))
        out.write(alignCommand(TextAlignMode.CENTER))

        // GS v 0 m xL xH yL yH [d1..dk]
        out.write(0x1D)
        out.write(0x76)
        out.write(0x30)
        out.write(0x00)
        out.write(widthBytes and 0xFF)
        out.write((widthBytes shr 8) and 0xFF)
        out.write(height and 0xFF)
        out.write((height shr 8) and 0xFF)

        val rowPixels = IntArray(width)
        for (y in 0 until height) {
            mono.getPixels(rowPixels, 0, width, 0, y, width, 1)
            for (byteIdx in 0 until widthBytes) {
                var b = 0
                for (bit in 0 until 8) {
                    val pixel = rowPixels[byteIdx * 8 + bit]
                    if (Color.red(pixel) < 128) {
                        b = b or (1 shl (7 - bit))
                    }
                }
                out.write(b)
            }
        }
        mono.recycle()

        out.write(feedLines(4))
        if (settings.autoCutPaper) {
            out.write(CUT_PARTIAL)
        }
        return out.toByteArray()
    }
}
