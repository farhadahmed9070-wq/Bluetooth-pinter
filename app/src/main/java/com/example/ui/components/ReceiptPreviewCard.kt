package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReceiptLineItem
import com.example.data.formatCents
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptPreviewCard(
    businessName: String,
    businessSubtitle: String,
    headerNote: String,
    footerNote: String,
    orderNumber: String,
    items: List<ReceiptLineItem>,
    currencySymbol: String,
    paperWidthMm: Int,
    showDate: Boolean,
    modifier: Modifier = Modifier
) {
    val maxPreviewWidth = if (paperWidthMm == 80) 380.dp else 300.dp
    val totalCents = items.sumOf { it.lineTotalCents }
    val paperBg = Color(0xFFFCFDFD)
    val inkColor = Color(0xFF111827)
    val mutedPaperInk = Color(0xFF64748B)
    val dateString = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US).format(Date())

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = paperBg,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 3.dp,
            modifier = Modifier.widthIn(max = maxPreviewWidth).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${paperWidthMm}mm THERMAL PREVIEW",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = mutedPaperInk,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = businessName.ifBlank { "Your Business Name" },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = inkColor,
                    textAlign = TextAlign.Center
                )
                if (businessSubtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = businessSubtitle,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = mutedPaperInk,
                        textAlign = TextAlign.Center
                    )
                }
                if (headerNote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = headerNote,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = inkColor,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                DashedPaperDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = orderNumber,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = inkColor
                    )
                    if (showDate) {
                        Text(
                            text = dateString,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = mutedPaperInk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                DashedPaperDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (items.isEmpty()) {
                    Text(
                        text = "No items added yet",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = mutedPaperInk,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.quantity}x ${item.name}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                color = inkColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Text(
                                text = formatCents(item.lineTotalCents, currencySymbol),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                color = inkColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                DashedPaperDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOTAL",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = inkColor
                    )
                    Text(
                        text = formatCents(totalCents, currencySymbol),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = inkColor
                    )
                }

                if (footerNote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    DashedPaperDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = footerNote,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = mutedPaperInk,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Subtle serrated thermal paper edge
        Canvas(
            modifier = Modifier
                .widthIn(max = maxPreviewWidth)
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val teeth = 24
            val step = size.width / teeth
            val path = Path().apply {
                moveTo(0f, 0f)
                for (i in 0 until teeth) {
                    lineTo(i * step + step / 2f, size.height)
                    lineTo((i + 1) * step, 0f)
                }
                close()
            }
            drawPath(path = path, color = paperBg)
        }
    }
}

@Composable
private fun DashedPaperDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = Color(0xFFCBD5E1)
    )
}
