package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.OrderStatus
import com.example.data.OrderType
import com.example.data.PrintOrder
import com.example.data.UiState
import com.example.data.formatCents
import com.example.ui.theme.CobaltContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoTertiary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OnCobaltContainer
import com.example.ui.theme.OnEmeraldContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrdersScreen(
    ordersState: UiState<List<PrintOrder>>,
    onNewOrderClick: () -> Unit,
    onReprintOrder: (PrintOrder) -> Unit,
    onDeleteOrder: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<OrderType?>(null) }
    var expandedOrderId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewOrderClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("orders_new_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New Order")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search order #, business, or printer...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("orders_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedType == null,
                                onClick = { selectedType = null },
                                label = { Text("All") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedType == OrderType.RECEIPT,
                                onClick = { selectedType = OrderType.RECEIPT },
                                label = { Text("Receipts") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedType == OrderType.TEXT,
                                onClick = { selectedType = OrderType.TEXT },
                                label = { Text("Text") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedType == OrderType.IMAGE,
                                onClick = { selectedType = OrderType.IMAGE },
                                label = { Text("Images") }
                            )
                        }
                    }
                }

                when (ordersState) {
                    UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = ordersState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is UiState.Success -> {
                        val filtered = ordersState.data.filter { order ->
                            val matchesType = selectedType == null || order.resolvedOrderType() == selectedType
                            val q = searchQuery.trim().lowercase()
                            val matchesQuery = q.isEmpty() ||
                                order.orderNumber.lowercase().contains(q) ||
                                order.title.lowercase().contains(q) ||
                                order.businessName.lowercase().contains(q) ||
                                order.printerName.lowercase().contains(q)
                            matchesType && matchesQuery
                        }

                        if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "No matching print orders",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Create a new order or print text/images to populate your history.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(onClick = onNewOrderClick, shape = RoundedCornerShape(12.dp)) {
                                        Text("Create Order")
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 92.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered, key = { it.id }) { order ->
                                    val isExpanded = expandedOrderId == order.id
                                    OrderHistoryCard(
                                        order = order,
                                        isExpanded = isExpanded,
                                        onToggleExpand = {
                                            expandedOrderId = if (isExpanded) null else order.id
                                        },
                                        onReprint = { onReprintOrder(order) },
                                        onDelete = { onDeleteOrder(order.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderHistoryCard(
    order: PrintOrder,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onReprint: () -> Unit,
    onDelete: () -> Unit
) {
    val status = order.resolvedStatus()
    val type = order.resolvedOrderType()
    val timestampText = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(
        order.createdAt?.toDate() ?: Date()
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggleExpand)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = order.orderNumber,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(shape = RoundedCornerShape(6.dp), color = CobaltContainer) {
                            Text(
                                text = type.wireValue.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnCobaltContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = order.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$timestampText • ${order.paperWidthMm}mm • ${order.printerName.ifBlank { "Queued" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (order.totalCents > 0) {
                        Text(
                            text = formatCents(order.totalCents, order.currencySymbol),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = "Toggle details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (order.items.isNotEmpty()) {
                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.quantity}x ${item.name}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                )
                                Text(
                                    text = formatCents(item.lineTotalCents, order.currencySymbol),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                )
                            }
                        }
                    } else if (order.textContent.isNotBlank()) {
                        Text(
                            text = order.textContent,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReprint,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reprint")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete order",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
