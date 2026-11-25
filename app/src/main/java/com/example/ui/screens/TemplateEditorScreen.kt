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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.example.data.PrinterSettings
import com.example.data.ReceiptLineItem
import com.example.data.ReceiptTemplate
import com.example.data.UiState
import com.example.ui.components.ReceiptPreviewCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlin.math.roundToLong

private data class TemplateItemDraft(
    val name: String,
    val qty: String,
    val price: String
) {
    fun toLineItem(): ReceiptLineItem? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val q = qty.toLongOrNull()?.coerceIn(1L, 9999L) ?: 1L
        val p = ((price.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong().coerceIn(0L, 100_000_000L)
        return ReceiptLineItem(clean.take(60), q, p)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    templateId: String?,
    templatesState: UiState<List<ReceiptTemplate>>,
    settings: PrinterSettings,
    isBusy: Boolean,
    onSaveTemplate: (ReceiptTemplate) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val existing = (templatesState as? UiState.Success)?.data?.firstOrNull { it.id == templateId }

    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "My Receipt Template") }
    var businessName by remember(existing?.id) { mutableStateOf(existing?.businessName ?: "My Business") }
    var businessSubtitle by remember(existing?.id) { mutableStateOf(existing?.businessSubtitle ?: "123 Main Street") }
    var headerNote by remember(existing?.id) { mutableStateOf(existing?.headerNote ?: "Tax Invoice") }
    var footerNote by remember(existing?.id) { mutableStateOf(existing?.footerNote ?: "Thank you for your visit!") }
    var paperWidthMm by remember(existing?.id) {
        mutableIntStateOf(existing?.paperWidthMm?.toInt() ?: settings.paperWidthMm)
    }
    var currencySymbol by remember(existing?.id) {
        mutableStateOf(existing?.currencySymbol ?: settings.defaultCurrency)
    }
    var showDate by remember(existing?.id) { mutableStateOf(existing?.showDate ?: true) }
    var showLogoHeader by remember(existing?.id) { mutableStateOf(existing?.showLogoHeader ?: true) }

    val itemsDraft = remember(existing?.id) {
        mutableStateListOf<TemplateItemDraft>().apply {
            if (existing != null && existing.defaultItems.isNotEmpty()) {
                existing.defaultItems.forEach {
                    add(
                        TemplateItemDraft(
                            it.name,
                            it.quantity.toString(),
                            String.format(Locale.US, "%.2f", it.unitPriceCents / 100.0)
                        )
                    )
                }
            } else {
                add(TemplateItemDraft("Sample Item", "1", "9.99"))
            }
        }
    }

    val previewItems = itemsDraft.mapNotNull { it.toLineItem() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New Receipt Template" else "Edit Template") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("template_editor_back")) {
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
                            OutlinedTextField(
                                value = name,
                                onValueChange = { if (it.length <= 60) name = it },
                                label = { Text("Template Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("template_name_input")
                            )
                            OutlinedTextField(
                                value = businessName,
                                onValueChange = { if (it.length <= 80) businessName = it },
                                label = { Text("Business Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("template_business_input")
                            )
                            OutlinedTextField(
                                value = businessSubtitle,
                                onValueChange = { if (it.length <= 120) businessSubtitle = it },
                                label = { Text("Subtitle / Address") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = headerNote,
                                onValueChange = { if (it.length <= 200) headerNote = it },
                                label = { Text("Header Note") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = footerNote,
                                onValueChange = { if (it.length <= 200) footerNote = it },
                                label = { Text("Footer Note") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
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
                                OutlinedTextField(
                                    value = currencySymbol,
                                    onValueChange = { if (it.length <= 5) currencySymbol = it },
                                    label = { Text("Currency") },
                                    singleLine = true,
                                    modifier = Modifier.width(96.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show Date & Time", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = showDate, onCheckedChange = { showDate = it })
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bold Business Header", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = showLogoHeader, onCheckedChange = { showLogoHeader = it })
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Default Line Items", style = MaterialTheme.typography.titleMedium)
                                TextButton(
                                    onClick = {
                                        if (itemsDraft.size < 25) {
                                            itemsDraft.add(TemplateItemDraft("", "1", "0.00"))
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add")
                                }
                            }

                            itemsDraft.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = row.name,
                                        onValueChange = { itemsDraft[idx] = row.copy(name = it.take(60)) },
                                        label = { Text("Item") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.4f)
                                    )
                                    OutlinedTextField(
                                        value = row.qty,
                                        onValueChange = { itemsDraft[idx] = row.copy(qty = it.filter(Char::isDigit).take(4)) },
                                        label = { Text("Qty") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        modifier = Modifier.width(60.dp)
                                    )
                                    OutlinedTextField(
                                        value = row.price,
                                        onValueChange = { raw ->
                                            itemsDraft[idx] = row.copy(price = raw.filter { it.isDigit() || it == '.' }.take(9))
                                        },
                                        label = { Text("Price") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        modifier = Modifier.width(84.dp)
                                    )
                                    IconButton(onClick = { if (itemsDraft.isNotEmpty()) itemsDraft.removeAt(idx) }) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Remove",
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
                        orderNumber = "TEMPLATE",
                        items = previewItems,
                        currencySymbol = currencySymbol.ifBlank { "$" },
                        paperWidthMm = paperWidthMm,
                        showDate = showDate
                    )
                }

                item {
                    Button(
                        enabled = !isBusy && name.isNotBlank() && businessName.isNotBlank(),
                        onClick = {
                            onSaveTemplate(
                                ReceiptTemplate(
                                    id = existing?.id ?: "",
                                    userId = existing?.userId ?: "",
                                    name = name.trim(),
                                    businessName = businessName.trim(),
                                    businessSubtitle = businessSubtitle.trim(),
                                    headerNote = headerNote.trim(),
                                    footerNote = footerNote.trim(),
                                    paperWidthMm = paperWidthMm.toLong(),
                                    currencySymbol = currencySymbol.trim().ifEmpty { "$" },
                                    showDate = showDate,
                                    showLogoHeader = showLogoHeader,
                                    defaultItems = previewItems,
                                    createdAt = existing?.createdAt,
                                    updatedAt = existing?.updatedAt
                                )
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_template_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.saveTemplate)
                    }
                }
            }
        }
    }
}
