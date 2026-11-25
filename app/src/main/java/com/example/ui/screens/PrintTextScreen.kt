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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.PrinterConnectionState
import com.example.bluetooth.TextAlignMode
import com.example.bluetooth.TextSizeMode
import com.example.data.PrinterSettings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintTextScreen(
    settings: PrinterSettings,
    connectionState: PrinterConnectionState,
    isBusy: Boolean,
    onPrintText: (
        text: String,
        align: TextAlignMode,
        size: TextSizeMode,
        bold: Boolean,
        feedLines: Int
    ) -> Unit,
    onFindPrinterClick: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    var text by remember {
        mutableStateOf(
            "DELIVERY NOTE\nOrder #4082\nDrop at Front Desk\nHandle with care"
        )
    }
    var align by remember { mutableStateOf(TextAlignMode.LEFT) }
    var sizeMode by remember { mutableStateOf(TextSizeMode.NORMAL) }
    var isBold by remember { mutableStateOf(false) }
    var feedLines by remember { mutableIntStateOf(3) }

    val isConnected = connectionState is PrinterConnectionState.Connected

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.printText) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("print_text_back")) {
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { if (it.length <= 2000) text = it },
                                label = { Text("Text to Print (${text.length}/2000)") },
                                minLines = 5,
                                maxLines = 10,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                modifier = Modifier.fillMaxWidth().testTag("print_text_input")
                            )

                            Text("Alignment & Style", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                    SegmentedButton(
                                        selected = align == TextAlignMode.LEFT,
                                        onClick = { align = TextAlignMode.LEFT },
                                        shape = SegmentedButtonDefaults.itemShape(0, 3)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.FormatAlignLeft, contentDescription = "Left")
                                    }
                                    SegmentedButton(
                                        selected = align == TextAlignMode.CENTER,
                                        onClick = { align = TextAlignMode.CENTER },
                                        shape = SegmentedButtonDefaults.itemShape(1, 3)
                                    ) {
                                        Icon(Icons.Rounded.FormatAlignCenter, contentDescription = "Center")
                                    }
                                    SegmentedButton(
                                        selected = align == TextAlignMode.RIGHT,
                                        onClick = { align = TextAlignMode.RIGHT },
                                        shape = SegmentedButtonDefaults.itemShape(2, 3)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.FormatAlignRight, contentDescription = "Right")
                                    }
                                }

                                FilterChip(
                                    selected = isBold,
                                    onClick = { isBold = !isBold },
                                    label = { Text("Bold") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.FormatBold, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }

                            Text("Text Size", style = MaterialTheme.typography.labelMedium)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                val modes = listOf(
                                    TextSizeMode.NORMAL to "Normal",
                                    TextSizeMode.DOUBLE_HEIGHT to "Tall",
                                    TextSizeMode.DOUBLE_WIDTH to "Wide",
                                    TextSizeMode.LARGE to "Large"
                                )
                                modes.forEachIndexed { idx, (mode, label) ->
                                    SegmentedButton(
                                        selected = sizeMode == mode,
                                        onClick = { sizeMode = mode },
                                        shape = SegmentedButtonDefaults.itemShape(idx, modes.size)
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Column {
                                Text(
                                    text = "Feed Lines After Print: $feedLines",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Slider(
                                    value = feedLines.toFloat(),
                                    onValueChange = { feedLines = it.toInt() },
                                    valueRange = 1f..8f,
                                    steps = 6
                                )
                            }
                        }
                    }
                }

                // Thermal paper live preview
                item {
                    val maxWidth = if (settings.paperWidthMm == 80) 380.dp else 300.dp
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            color = Color(0xFFFCFDFD),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shadowElevation = 2.dp,
                            modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "${settings.paperWidthMm}mm TEXT PREVIEW",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = text.ifBlank { "(Empty text)" },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = when (sizeMode) {
                                        TextSizeMode.NORMAL -> 13.sp
                                        TextSizeMode.DOUBLE_HEIGHT -> 17.sp
                                        TextSizeMode.DOUBLE_WIDTH -> 16.sp
                                        TextSizeMode.LARGE -> 20.sp
                                    },
                                    textAlign = when (align) {
                                        TextAlignMode.LEFT -> TextAlign.Start
                                        TextAlignMode.CENTER -> TextAlign.Center
                                        TextAlignMode.RIGHT -> TextAlign.End
                                    },
                                    color = Color(0xFF111827),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
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
                                    text = "No printer connected. Printing will save to Queued Orders.",
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
                    Button(
                        enabled = !isBusy && text.isNotBlank(),
                        onClick = { onPrintText(text, align, sizeMode, isBold, feedLines) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("print_text_submit_button")
                    ) {
                        Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isConnected) strings.printNow else "Queue Text Job")
                    }
                }
            }
        }
    }
}
