package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.bluetooth.BluetoothPrinterManager
import com.example.bluetooth.PrinterConnectionState
import com.example.data.PrinterRepository
import com.example.data.SettingsRepository
import com.example.ui.PrintViewModel
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NewOrderScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.PrintImageScreen
import com.example.ui.screens.PrintTextScreen
import com.example.ui.screens.PrinterScanScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TemplateEditorScreen
import com.example.ui.screens.TemplatesScreen
import com.example.ui.theme.BluetoothPrintTheme
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.OnEmeraldContainer
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object Routes {
    const val HOME = "home"
    const val ORDERS = "orders"
    const val TEMPLATES = "templates"
    const val SETTINGS = "settings"
    const val PRINTER_SCAN = "printer_scan"
    const val NEW_ORDER = "new_order"
    const val PRINT_TEXT = "print_text"
    const val PRINT_IMAGE = "print_image"
    const val TEMPLATE_EDITOR = "template_editor"
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothPrintTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            BluetoothPrintApp(
                currentUserId = user.uid,
                userEmail = user.email
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BluetoothPrintApp(
    currentUserId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: PrintViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PrintViewModel(
                    repository = PrinterRepository(db),
                    settingsRepository = SettingsRepository(app),
                    bluetoothManager = BluetoothPrinterManager(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val templatesState by viewModel.templatesState.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val isBtSupported by viewModel.isBluetoothSupported.collectAsStateWithLifecycle()
    val isBtEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val localizedStrings = remember(settings.language) { stringsFor(settings.language) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val primaryTabs = listOf(
        BottomNavTab(Routes.HOME, localizedStrings.navHome, Icons.Rounded.Home, Icons.Outlined.Home, "tab_home"),
        BottomNavTab(Routes.ORDERS, localizedStrings.navOrders, Icons.AutoMirrored.Rounded.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "tab_orders"),
        BottomNavTab(Routes.TEMPLATES, localizedStrings.navTemplates, Icons.Rounded.Description, Icons.Outlined.Description, "tab_templates"),
        BottomNavTab(Routes.SETTINGS, localizedStrings.navSettings, Icons.Rounded.Settings, Icons.Outlined.Settings, "tab_settings")
    )

    val isTopLevelRoute = currentRoute in primaryTabs.map { it.route }

    CompositionLocalProvider(LocalAppStrings provides localizedStrings) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (isTopLevelRoute) {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (currentRoute) {
                                    Routes.ORDERS -> localizedStrings.navOrders
                                    Routes.TEMPLATES -> localizedStrings.receiptTemplates
                                    Routes.SETTINGS -> localizedStrings.navSettings
                                    else -> localizedStrings.appTitle
                                },
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },
                        actions = {
                            val isConnected = connectionState is PrinterConnectionState.Connected
                            Surface(
                                onClick = { navController.navigate(Routes.PRINTER_SCAN) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isConnected) EmeraldContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 12.dp).testTag("top_printer_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothDisabled,
                                        contentDescription = "Printer status",
                                        tint = if (isConnected) OnEmeraldContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isConnected) localizedStrings.connectedLabel else "${settings.paperWidthMm}mm",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isConnected) OnEmeraldContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (isTopLevelRoute) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        primaryTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        connectionState = connectionState,
                        settings = settings,
                        templatesState = templatesState,
                        ordersState = ordersState,
                        onFindPrinterClick = { navController.navigate(Routes.PRINTER_SCAN) },
                        onDisconnectPrinter = { viewModel.disconnectPrinter() },
                        onTestPrint = { viewModel.printTestPage() },
                        onNewOrderClick = { navController.navigate("${Routes.NEW_ORDER}?templateId=") },
                        onPrintReceiptsClick = { navController.navigate("${Routes.NEW_ORDER}?templateId=") },
                        onPrintTextClick = { navController.navigate(Routes.PRINT_TEXT) },
                        onPrintImagesClick = { navController.navigate(Routes.PRINT_IMAGE) },
                        onTemplatesClick = {
                            navController.navigate(Routes.TEMPLATES) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOrdersClick = {
                            navController.navigate(Routes.ORDERS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onReprintOrder = { viewModel.reprintOrder(it) }
                    )
                }

                composable(Routes.ORDERS) {
                    OrdersScreen(
                        ordersState = ordersState,
                        onNewOrderClick = { navController.navigate("${Routes.NEW_ORDER}?templateId=") },
                        onReprintOrder = { viewModel.reprintOrder(it) },
                        onDeleteOrder = { viewModel.deleteOrder(it) }
                    )
                }

                composable(Routes.TEMPLATES) {
                    TemplatesScreen(
                        templatesState = templatesState,
                        onCreateTemplate = { navController.navigate("${Routes.TEMPLATE_EDITOR}?templateId=") },
                        onEditTemplate = { id -> navController.navigate("${Routes.TEMPLATE_EDITOR}?templateId=$id") },
                        onUseTemplateInOrder = { id -> navController.navigate("${Routes.NEW_ORDER}?templateId=$id") },
                        onDeleteTemplate = { viewModel.deleteTemplate(it) },
                        onSeedStarters = { viewModel.seedStarterTemplates() }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        connectionState = connectionState,
                        userEmail = userEmail,
                        onFindPrinterClick = { navController.navigate(Routes.PRINTER_SCAN) },
                        onDisconnectPrinter = { viewModel.disconnectPrinter() },
                        onTestPrint = { viewModel.printTestPage() },
                        onPaperWidthChange = { viewModel.setPaperWidth(it) },
                        onDensityChange = { viewModel.setPrintDensity(it) },
                        onAutoCutChange = { viewModel.setAutoCutPaper(it) },
                        onCurrencyChange = { viewModel.setDefaultCurrency(it) },
                        onLanguageChange = { viewModel.setLanguage(it) },
                        onSignOutClick = {
                            signOut(credentialManager = credentialManager, scope = scope)
                        }
                    )
                }

                composable(Routes.PRINTER_SCAN) {
                    PrinterScanScreen(
                        isBluetoothSupported = isBtSupported,
                        isBluetoothEnabled = isBtEnabled,
                        isScanning = isScanning,
                        devices = discoveredDevices,
                        connectionState = connectionState,
                        onRefreshState = { viewModel.refreshBluetoothState() },
                        onStartScan = { viewModel.startPrinterScan() },
                        onStopScan = { viewModel.stopPrinterScan() },
                        onConnectDevice = { viewModel.connectPrinter(it) },
                        onDisconnect = { viewModel.disconnectPrinter() },
                        onTestPrint = { viewModel.printTestPage() },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "${Routes.NEW_ORDER}?templateId={templateId}",
                    arguments = listOf(navArgument("templateId") {
                        type = NavType.StringType
                        defaultValue = ""
                    })
                ) { entry ->
                    val tplId = entry.arguments?.getString("templateId")?.ifBlank { null }
                    NewOrderScreen(
                        initialTemplateId = tplId,
                        templatesState = templatesState,
                        settings = settings,
                        connectionState = connectionState,
                        isBusy = isBusy,
                        onSubmitOrder = { biz, sub, header, footer, items, widthMm, currency, showDate, printNow ->
                            viewModel.submitReceiptOrder(
                                businessName = biz,
                                businessSubtitle = sub,
                                headerNote = header,
                                footerNote = footer,
                                items = items,
                                paperWidthMm = widthMm,
                                currencySymbol = currency,
                                showDate = showDate,
                                printImmediately = printNow,
                                onComplete = { navController.popBackStack() }
                            )
                        },
                        onFindPrinterClick = { navController.navigate(Routes.PRINTER_SCAN) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "${Routes.TEMPLATE_EDITOR}?templateId={templateId}",
                    arguments = listOf(navArgument("templateId") {
                        type = NavType.StringType
                        defaultValue = ""
                    })
                ) { entry ->
                    val tplId = entry.arguments?.getString("templateId")?.ifBlank { null }
                    TemplateEditorScreen(
                        templateId = tplId,
                        templatesState = templatesState,
                        settings = settings,
                        isBusy = isBusy,
                        onSaveTemplate = { template ->
                            viewModel.saveTemplate(template) {
                                navController.popBackStack()
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.PRINT_TEXT) {
                    PrintTextScreen(
                        settings = settings,
                        connectionState = connectionState,
                        isBusy = isBusy,
                        onPrintText = { text, align, size, bold, feed ->
                            viewModel.printTextJob(text, align, size, bold, feed) {
                                navController.popBackStack()
                            }
                        },
                        onFindPrinterClick = { navController.navigate(Routes.PRINTER_SCAN) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.PRINT_IMAGE) {
                    PrintImageScreen(
                        settings = settings,
                        connectionState = connectionState,
                        isBusy = isBusy,
                        onPrintImage = { bmp, label, threshold, dither ->
                            viewModel.printImageJob(bmp, label, threshold, dither) {
                                navController.popBackStack()
                            }
                        },
                        onFindPrinterClick = { navController.navigate(Routes.PRINTER_SCAN) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
