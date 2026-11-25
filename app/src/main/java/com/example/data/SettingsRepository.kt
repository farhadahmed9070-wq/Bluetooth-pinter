package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.printerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bluetooth_print_settings"
)

class SettingsRepository(private val context: Context) {
    private val dataStore = context.applicationContext.printerSettingsDataStore

    private object Keys {
        val PAPER_WIDTH_MM = intPreferencesKey("paper_width_mm")
        val PRINT_DENSITY = intPreferencesKey("print_density")
        val AUTO_CUT_PAPER = booleanPreferencesKey("auto_cut_paper")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val LANGUAGE = stringPreferencesKey("language")
        val LAST_PRINTER_NAME = stringPreferencesKey("last_printer_name")
        val LAST_PRINTER_ADDRESS = stringPreferencesKey("last_printer_address")
    }

    val settingsFlow: Flow<PrinterSettings> = dataStore.data.map { prefs ->
        PrinterSettings(
            paperWidthMm = if (prefs[Keys.PAPER_WIDTH_MM] == 80) 80 else 58,
            printDensity = PrintDensity.fromLevel(prefs[Keys.PRINT_DENSITY] ?: PrintDensity.NORMAL.level),
            autoCutPaper = prefs[Keys.AUTO_CUT_PAPER] ?: true,
            defaultCurrency = prefs[Keys.DEFAULT_CURRENCY] ?: "$",
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            lastPrinterName = prefs[Keys.LAST_PRINTER_NAME] ?: "",
            lastPrinterAddress = prefs[Keys.LAST_PRINTER_ADDRESS] ?: ""
        )
    }

    suspend fun setPaperWidth(widthMm: Int) {
        val validWidth = if (widthMm == 80) 80 else 58
        dataStore.edit { it[Keys.PAPER_WIDTH_MM] = validWidth }
    }

    suspend fun setPrintDensity(density: PrintDensity) {
        dataStore.edit { it[Keys.PRINT_DENSITY] = density.level }
    }

    suspend fun setAutoCutPaper(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_CUT_PAPER] = enabled }
    }

    suspend fun setDefaultCurrency(symbol: String) {
        val safeSymbol = symbol.trim().take(5).ifEmpty { "$" }
        dataStore.edit { it[Keys.DEFAULT_CURRENCY] = safeSymbol }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun saveLastConnectedPrinter(name: String, address: String) {
        dataStore.edit {
            it[Keys.LAST_PRINTER_NAME] = name
            it[Keys.LAST_PRINTER_ADDRESS] = address
        }
    }
}
