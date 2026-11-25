package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bluetooth.PrinterConnectionState
import com.example.data.OrderStatus
import com.example.data.PrintOrder
import com.example.data.PrinterSettings
import com.example.data.ReceiptTemplate
import com.example.data.UiState
import com.example.data.formatCents
import com.example.ui.components.PrinterStatusCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CobaltContainer
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoTertiary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OnCobaltContainer
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.OnIndigoContainer
import com.example.ui.theme.OnTealContainer
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealSecondary

@Composable
fun HomeScreen(
    connectionState: PrinterConnectionState,
    settings: PrinterSettings,
    templatesState: UiState<List<ReceiptTemplate>>,
    ordersState: UiState<List<PrintOrder>>,
    onFindPrinterClick: () -> Unit,
    onDisconnectPrinter: () -> Unit,
    onTestPrint: () -> Unit,
    onNewOrderClick: () -> Unit,
    onPrintReceiptsClick: () -> Unit,
    onPrintTextClick: () -> Unit,
    onPrintImagesClick: () -> Unit,
    onTemplatesClick: () -> Unit,
    onOrdersClick: () -> Unit,
    onReprintOrder: (PrintOrder) -> Unit
) {
    val strings = LocalAppStrings.current
    val templates = (templatesState as? UiState.Success)?.data ?: emptyList()
    val orders = (ordersState as? UiState.Success)?.data ?: emptyList()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PrinterStatusCard(
                    connectionState = connectionState,
                    paperWidthMm = settings.paperWidthMm,
                    onFindPrinterClick = onFindPrinterClick,
                    onDisconnectClick = onDisconnectPrinter,
                    onTestPrintClick = onTestPrint
                )
            }

            // Large Blue "Find Printer" CTA Button (Feature 4)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onFindPrinterClick)
                        .testTag("find_printer_button")
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(CobaltPrimary, CobaltDark)
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                                contentDescription = strings.findPrinter,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.findPrinter,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.findPrinterSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }

            // 2x2 Quick Print Dashboard Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardActionCard(
                            title = strings.newOrder,
                            subtitle = strings.newOrderDesc,
                            icon = Icons.Rounded.AddShoppingCart,
                            containerColor = CobaltContainer,
                            iconTint = OnCobaltContainer,
                            onClick = onNewOrderClick,
                            modifier = Modifier.weight(1f).testTag("home_new_order_card")
                        )
                        DashboardActionCard(
                            title = strings.printReceipts,
                            subtitle = strings.printReceiptsDesc,
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            containerColor = IndigoContainer,
                            iconTint = OnIndigoContainer,
                            onClick = onPrintReceiptsClick,
                            modifier = Modifier.weight(1f).testTag("home_print_receipts_card")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardActionCard(
                            title = strings.printText,
                            subtitle = strings.printTextDesc,
                            icon = Icons.Rounded.TextFields,
                            containerColor = TealContainer,
                            iconTint = OnTealContainer,
                            onClick = onPrintTextClick,
                            modifier = Modifier.weight(1f).testTag("home_print_text_card")
                        )
                        DashboardActionCard(
                            title = strings.printImages,
                            subtitle = strings.printImagesDesc,
                            icon = Icons.Rounded.PhotoLibrary,
                            containerColor = EmeraldContainer,
                            iconTint = OnEmeraldContainer,
                            onClick = onPrintImagesClick,
                            modifier = Modifier.weight(1f).testTag("home_print_images_card")
                        )
                    }
                }
            }

            // Receipt Templates Shortcut Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onTemplatesClick)
                        .testTag("home_templates_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CobaltContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = strings.receiptTemplates,
                                tint = CobaltPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.receiptTemplates,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${templates.size} saved • 58mm & 80mm layouts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recent Orders Preview
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.recentOrders,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onOrdersClick, modifier = Modifier.testTag("home_view_orders_button")) {
                        Text(strings.viewAll)
                    }
                }

                if (orders.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Print,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No print orders yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap New Order or Print Text to create your first job.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        orders.take(3).forEach { order ->
                            RecentOrderRow(
                                order = order,
                                onReprint = { onReprintOrder(order) },
                                onClick = onOrdersClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentOrderRow(
    order: PrintOrder,
    onReprint: () -> Unit,
    onClick: () -> Unit
) {
    val status = order.resolvedStatus()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.orderNumber,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (status) {
                            OrderStatus.PRINTED -> EmeraldContainer
                            OrderStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                            OrderStatus.QUEUED -> IndigoContainer
                        }
                    ) {
                        Text(
                            text = status.wireValue.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (status) {
                                OrderStatus.PRINTED -> OnEmeraldContainer
                                OrderStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                                OrderStatus.QUEUED -> IndigoTertiary
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = order.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (order.totalCents > 0) {
                    Text(
                        text = formatCents(order.totalCents, order.currencySymbol),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(onClick = onReprint) {
                Icon(
                    imageVector = Icons.Rounded.Print,
                    contentDescription = "Reprint",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Print")
            }
        }
    }
}
