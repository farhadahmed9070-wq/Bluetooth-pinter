package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bluetooth.PrinterConnectionState
import com.example.data.PrinterSettings
import com.example.data.ReceiptLineItem
import com.example.data.ReceiptTemplate
import com.example.data.UiState
import com.example.ui.components.ReceiptPreviewCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToLong

private data class EditableOrderItem(
    val name: String,
    val quantityText: String,
    val priceText: String
) {
    fun toReceiptLineItem(): ReceiptLineItem? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return null
        val qty = quantityText.toLongOrNull()?.coerceIn(1L, 9999L) ?: 1L
        val priceDouble = priceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val cents = (priceDouble * 100.0).roundToLong().coerceIn(0L, 100_000_000L)
        return ReceiptLineItem(cleanName.take(60), qty, cents)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(
    initialTemplateId: String?,
    templatesState: UiState<List<ReceiptTemplate>>,
    settings: PrinterSettings,
    connectionState: PrinterConnectionState,
    isBusy: Boolean,
    onSubmitOrder: (
        businessName: String,
        businessSubtitle: String,
        headerNote: String,
        footerNote: String,
        items: List<ReceiptLineItem>,
        paperWidthMm: Int,
        currencySymbol: String,
        showDate: Boolean,
        printImmediately: Boolean
    ) -> Unit,
    onFindPrinterClick: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val templates = (templatesState as? UiState.Success)?.data ?: emptyList()

    var selectedTemplateId by remember { mutableStateOf<String?>(initialTemplateId) }
    var businessName by remember { mutableStateOf("Harbor Roast Coffee") }
    var businessSubtitle by remember { mutableStateOf("42 Pier Ave • (555) 204-8891") }
    var headerNote by remember { mutableStateOf("Customer Receipt") }
    var footerNote by remember { mutableStateOf("Thank you for your business!") }
    var paperWidthMm by remember { mutableIntStateOf(settings.paperWidthMm) }
    var currencySymbol by remember { mutableStateOf(settings.defaultCurrency) }
    var showDate by remember { mutableStateOf(true) }
    var appliedInitial by remember { mutableStateOf(false) }

    val lineItems = remember {
        mutableStateListOf(
            EditableOrderItem("Artisan Latte", "1", "4.50"),
            EditableOrderItem("Butter Croissant", "2", "3.25")
        )
    }

    fun applyTemplate(tpl: ReceiptTemplate) {
        selectedTemplateId = tpl.id
        businessName = tpl.businessName
        businessSubtitle = tpl.businessSubtitle
        headerNote = tpl.headerNote
        footerNote = tpl.footerNote
        paperWidthMm = if (tpl.paperWidthMm == 80L) 80 else 58
        currencySymbol = tpl.currencySymbol
        showDate = tpl.showDate
        if (tpl.defaultItems.isNotEmpty()) {
            lineItems.clear()
            tpl.defaultItems.forEach { item ->
                val priceFormatted = String.format(java.util.Locale.US, "%.2f", item.unitPriceCents / 100.0)
                lineItems.add(EditableOrderItem(item.name, item.quantity.toString(), priceFormatted))
            }
        }
    }

    LaunchedEffect(templates, initialTemplateId) {
        if (!appliedInitial && !initialTemplateId.isNullOrBlank()) {
            templates.firstOrNull { it.id == initialTemplateId }?.let {
                applyTemplate(it)
                appliedInitial = true
            }
        }
    }

    val previewItems = lineItems.mapNotNull { it.toReceiptLineItem() }
    val isConnected = connectionState is PrinterConnectionState.Connected

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.newOrder) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("new_order_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (templates.isNotEmpty()) {
                    item {
                        Text(
                            text = "Load From Template",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(templates, key = { it.id }) { tpl ->
                                FilterChip(
                                    selected = selectedTemplateId == tpl.id,
                                    onClick = { applyTemplate(tpl) },
                                    label = { Text(tpl.name) }
                                )
                            }
                        }
                    }
                }

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
                            Text(
                                text = "Business & Receipt Details",
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedTextField(
                                value = businessName,
                                onValueChange = { if (it.length <= 80) businessName = it },
                                label = { Text("Business Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("order_business_name_input")
                            )
                            OutlinedTextField(
                                value = businessSubtitle,
                                onValueChange = { if (it.length <= 120) businessSubtitle = it },
                                label = { Text("Address / Phone") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Paper Width",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = paperWidthMm == 58,
                                            onClick = { paperWidthMm = 58 },
                                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                                        ) { Text("58mm") }
                                        SegmentedButton(
                                            selected = paperWidthMm == 80,
                                            onClick = { paperWidthMm = 80 },
                                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                                        ) { Text("80mm") }
                                    }
                                }
                                OutlinedTextField(
                                    value = currencySymbol,
                                    onValueChange = { if (it.length <= 5) currencySymbol = it },
                                    label = { Text("Currency") },
                                    singleLine = true,
                                    modifier = Modifier.width(96.dp)
                                )
                            }
                            OutlinedTextField(
                                value = footerNote,
                                onValueChange = { if (it.length <= 200) footerNote = it },
                                label = { Text("Footer Message") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Order Items (${previewItems.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                TextButton(
                                    onClick = {
                                        if (lineItems.size < 50) {
                                            lineItems.add(EditableOrderItem("", "1", "0.00"))
                                        }
                                    },
                                    modifier = Modifier.testTag("add_order_item_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Item")
                                }
                            }

                            lineItems.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = row.name,
                                        onValueChange = { lineItems[idx] = row.copy(name = it.take(60)) },
                                        label = { Text("Item") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.5f)
                                    )
                                    OutlinedTextField(
                                        value = row.quantityText,
                                        onValueChange = { lineItems[idx] = row.copy(quantityText = it.filter(Char::isDigit).take(4)) },
                                        label = { Text("Qty") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        modifier = Modifier.width(64.dp)
                                    )
                                    OutlinedTextField(
                                        value = row.priceText,
                                        onValueChange = { filtered ->
                                            val clean = filtered.filter { it.isDigit() || it == '.' }.take(9)
                                            lineItems[idx] = row.copy(priceText = clean)
                                        },
                                        label = { Text("Price") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        modifier = Modifier.width(88.dp)
                                    )
                                    IconButton(
                                        onClick = { if (lineItems.size > 1) lineItems.removeAt(idx) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Remove item",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    ReceiptPreviewCard(
                        businessName = businessName,
                        businessSubtitle = businessSubtitle,
                        headerNote = headerNote,
                        footerNote = footerNote,
                        orderNumber = "PREVIEW",
                        items = previewItems,
                        currencySymbol = currencySymbol.ifBlank { "$" },
                        paperWidthMm = paperWidthMm,
                        showDate = showDate
                    )
                }

                if (!isConnected) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "No printer connected. Save as Queued or connect now.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onFindPrinterClick) {
                                    Text("Connect")
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            enabled = !isBusy && previewItems.isNotEmpty() && businessName.isNotBlank(),
                            onClick = {
                                onSubmitOrder(
                                    businessName,
                                    businessSubtitle,
                                    headerNote,
                                    footerNote,
                                    previewItems,
                                    paperWidthMm,
                                    currencySymbol.ifBlank { "$" },
                                    showDate,
                                    false
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(52.dp).testTag("save_order_button")
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.saveOrder)
                        }

                        Button(
                            enabled = !isBusy && previewItems.isNotEmpty() && businessName.isNotBlank(),
                            onClick = {
                                onSubmitOrder(
                                    businessName,
                                    businessSubtitle,
                                    headerNote,
                                    footerNote,
                                    previewItems,
                                    paperWidthMm,
                                    currencySymbol.ifBlank { "$" },
                                    showDate,
                                    true
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(52.dp).testTag("print_receipt_button")
                        ) {
                            Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.printNow)
                        }
                    }
                }
            }
        }
    }
}
