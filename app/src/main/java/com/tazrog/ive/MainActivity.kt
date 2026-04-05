package com.tazrog.ive

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.tazrog.ive.ui.theme.IvETheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanceTrackerApp()
        }
    }
}

class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            null -> AutoBackupScheduler.sync(context)
            else -> {
                runCatching {
                    val backupUri = FinanceStorage.createAutoBackupDocument(context)
                    FinanceStorage.exportBackup(
                        context = context,
                        uri = backupUri,
                        backupData = FinanceStorage.buildBackupData(context)
                    )
                }
                AutoBackupScheduler.sync(context)
            }
        }
    }
}

private enum class TransactionType {
    INCOME,
    EXPENSE
}

private enum class AppSection(val title: String, val subtitle: String) {
    HOME("Home", "Add a transaction and review this year's income versus expenses."),
    CATEGORIES("Categories", "Manage the categories available for your transactions."),
    STATS("Stats", "View summaries and charts by month, year, and category."),
    TRANSACTIONS("Transactions", "Search, edit, and remove saved transactions."),
    HINTS("Hints", "Read simple guidance for using I>E."),
    SETTINGS("Settings", "Choose your currency, theme, backups, and hints.")
}

private data class FinanceEntry(
    val id: String,
    val type: TransactionType,
    val dateMillis: Long,
    val category: String,
    val amountCents: Long
)

private data class MonthlyTotals(
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long
)

private data class YearlyTotals(
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long
)

private data class TransactionDraft(
    val type: TransactionType,
    val dateMillis: Long,
    val category: String,
    val amountInput: String
)

private data class PendingTransaction(
    val type: TransactionType,
    val dateMillis: Long,
    val category: String,
    val amountCents: Long
)

private data class PendingCategoryDelete(
    val category: String,
    val transactionCount: Int
)

private data class ChartBarGroup(
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long
)

private data class CurrencyOption(
    val code: String,
    val label: String
)

private data class BackupData(
    val categories: List<String>,
    val entries: List<FinanceEntry>,
    val currencyCode: String,
    val themeMode: ThemeMode,
    val hintsEnabled: Boolean
)

private const val allYearMonth = -1
private const val MAX_CATEGORY_NAME_LENGTH = 10

private enum class ThemeMode(val label: String) {
    SYSTEM("Use System"),
    LIGHT("Light Background"),
    DARK("Dark Background")
}

private enum class StatsDisplayMode(val label: String) {
    GRAPH("Chart"),
    VALUES("Values")
}

private object FinanceStorage {
    private const val PREFS_NAME = "finance_tracker"
    private const val KEY_CATEGORIES = "categories"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_CURRENCY = "currency"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_HINTS_ENABLED = "hints_enabled"
    private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
    private const val KEY_AUTO_BACKUP_MINUTES = "auto_backup_minutes"
    private const val KEY_AUTO_BACKUP_TREE_URI = "auto_backup_tree_uri"

    private val defaultCategories = listOf(
        "Salary",
        "Freelance",
        "Housing",
        "Food",
        "Transport",
        "Utilities"
    )

    fun loadCategories(context: Context): List<String> {
        val prefs = prefs(context)
        val saved = prefs.getString(KEY_CATEGORIES, null) ?: return defaultCategories
        val array = JSONArray(saved)
        return buildList {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
        }.ifEmpty { defaultCategories }
    }

    fun saveCategories(context: Context, categories: List<String>) {
        val array = JSONArray()
        categories.forEach(array::put)
        prefs(context).edit().putString(KEY_CATEGORIES, array.toString()).apply()
    }

    fun loadEntries(context: Context): List<FinanceEntry> {
        val saved = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        val array = JSONArray(saved)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    FinanceEntry(
                        id = item.getString("id"),
                        type = TransactionType.valueOf(item.getString("type")),
                        dateMillis = item.getLong("dateMillis"),
                        category = item.getString("category"),
                        amountCents = item.getLong("amountCents")
                    )
                )
            }
        }
    }

    fun saveEntries(context: Context, entries: List<FinanceEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("type", entry.type.name)
                    put("dateMillis", entry.dateMillis)
                    put("category", entry.category)
                    put("amountCents", entry.amountCents)
                }
            )
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
        YearIncomeExpenseWidgetUpdater.updateAll(context)
    }

    fun loadCurrencyCode(context: Context): String {
        return prefs(context).getString(KEY_CURRENCY, defaultCurrencyCode) ?: defaultCurrencyCode
    }

    fun saveCurrencyCode(context: Context, currencyCode: String) {
        prefs(context).edit().putString(KEY_CURRENCY, currencyCode).apply()
        YearIncomeExpenseWidgetUpdater.updateAll(context)
    }

    fun loadThemeMode(context: Context): ThemeMode {
        val raw = prefs(context).getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    fun saveThemeMode(context: Context, themeMode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, themeMode.name).apply()
    }

    fun loadHintsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_HINTS_ENABLED, true)
    }

    fun saveHintsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HINTS_ENABLED, enabled).apply()
    }

    fun loadAutoBackupEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
    }

    fun saveAutoBackupEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
    }

    fun loadAutoBackupMinutes(context: Context): Int {
        return prefs(context).getInt(KEY_AUTO_BACKUP_MINUTES, 2 * 60)
    }

    fun saveAutoBackupMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_AUTO_BACKUP_MINUTES, minutes).apply()
    }

    fun loadAutoBackupTreeUri(context: Context): String? {
        return prefs(context).getString(KEY_AUTO_BACKUP_TREE_URI, null)
    }

    fun saveAutoBackupTreeUri(context: Context, uri: String?) {
        prefs(context).edit().putString(KEY_AUTO_BACKUP_TREE_URI, uri).apply()
    }

    fun buildBackupData(context: Context): BackupData {
        return BackupData(
            categories = loadCategories(context),
            entries = loadEntries(context),
            currencyCode = loadCurrencyCode(context),
            themeMode = loadThemeMode(context),
            hintsEnabled = loadHintsEnabled(context)
        )
    }

    fun createAutoBackupDocument(context: Context): Uri {
        val treeUri = loadAutoBackupTreeUri(context)?.let(Uri::parse)
            ?: error("Select a backup folder before enabling automatic backup.")
        val treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val fileName = "ive-backup-${backupFileTimestamp()}.json"
        return DocumentsContract.createDocument(
            context.contentResolver,
            treeDocumentUri,
            "application/json",
            fileName
        ) ?: error("Unable to create the automatic backup file.")
    }

    fun exportBackup(context: Context, uri: Uri, backupData: BackupData) {
        val payload = JSONObject().apply {
            put("version", 1)
            put("currencyCode", backupData.currencyCode)
            put("themeMode", backupData.themeMode.name)
            put("hintsEnabled", backupData.hintsEnabled)
            put("categories", JSONArray().apply {
                backupData.categories.forEach(::put)
            })
            put("entries", JSONArray().apply {
                backupData.entries.forEach { entry ->
                    put(
                        JSONObject().apply {
                            put("id", entry.id)
                            put("type", entry.type.name)
                            put("dateMillis", entry.dateMillis)
                            put("category", entry.category)
                            put("amountCents", entry.amountCents)
                        }
                    )
                }
            })
        }.toString(2)

        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(payload)
        } ?: error("Unable to open export file.")
    }

    fun importBackup(context: Context, uri: Uri): BackupData {
        val contents = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        } ?: error("Unable to open backup file.")

        val root = JSONObject(contents)
        val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
        val entriesArray = root.optJSONArray("entries") ?: JSONArray()
        val importedCurrencyCode = root.optString("currencyCode", defaultCurrencyCode)
            .takeIf { code -> commonCurrencies.any { it.code == code } }
            ?: defaultCurrencyCode
        val importedThemeMode = ThemeMode.entries.firstOrNull {
            it.name == root.optString("themeMode", ThemeMode.SYSTEM.name)
        } ?: ThemeMode.SYSTEM
        val importedHintsEnabled = root.optBoolean("hintsEnabled", true)

        val categories = buildList {
            for (index in 0 until categoriesArray.length()) {
                add(categoriesArray.getString(index))
            }
        }.ifEmpty { defaultCategories }

        val entries = buildList {
            for (index in 0 until entriesArray.length()) {
                val item = entriesArray.getJSONObject(index)
                add(
                    FinanceEntry(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        type = TransactionType.valueOf(item.getString("type")),
                        dateMillis = item.getLong("dateMillis"),
                        category = item.getString("category"),
                        amountCents = item.getLong("amountCents")
                    )
                )
            }
        }

        return BackupData(
            categories = categories,
            entries = entries,
            currencyCode = importedCurrencyCode,
            themeMode = importedThemeMode,
            hintsEnabled = importedHintsEnabled
        )
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

private val commonCurrencies = listOf(
    CurrencyOption("USD", "US Dollar (USD)"),
    CurrencyOption("EUR", "Euro (EUR)"),
    CurrencyOption("GBP", "British Pound (GBP)"),
    CurrencyOption("JPY", "Japanese Yen (JPY)"),
    CurrencyOption("CNY", "Chinese Yuan (CNY)"),
    CurrencyOption("AUD", "Australian Dollar (AUD)"),
    CurrencyOption("CAD", "Canadian Dollar (CAD)"),
    CurrencyOption("CHF", "Swiss Franc (CHF)"),
    CurrencyOption("INR", "Indian Rupee (INR)"),
    CurrencyOption("MXN", "Mexican Peso (MXN)")
)

private const val defaultCurrencyCode = "USD"
private const val autoBackupRequestCode = 4201

private object AutoBackupScheduler {
    fun sync(context: Context) {
        if (!FinanceStorage.loadAutoBackupEnabled(context) ||
            FinanceStorage.loadAutoBackupTreeUri(context).isNullOrBlank()
        ) {
            cancel(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextAutoBackupTriggerAt(FinanceStorage.loadAutoBackupMinutes(context)),
            pendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AutoBackupReceiver::class.java).apply {
            action = "com.tazrog.ive.AUTO_BACKUP"
        }
        return PendingIntent.getBroadcast(
            context,
            autoBackupRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

@Composable
private fun FinanceTrackerApp() {
    val appContext = LocalContext.current
    val categories = remember { mutableStateListOf<String>() }
    val entries = remember { mutableStateListOf<FinanceEntry>() }
    var currencyCode by rememberSaveable { mutableStateOf(defaultCurrencyCode) }
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM.name) }
    var hintsEnabled by rememberSaveable { mutableStateOf(true) }
    var autoBackupEnabled by rememberSaveable { mutableStateOf(false) }
    var autoBackupMinutes by rememberSaveable { mutableStateOf(2 * 60) }
    var autoBackupTreeUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showTitleScreen by rememberSaveable { mutableStateOf(true) }
    val selectedThemeMode = ThemeMode.valueOf(themeMode)
    val useDarkTheme = when (selectedThemeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    LaunchedEffect(Unit) {
        categories.clear()
        categories.addAll(FinanceStorage.loadCategories(appContext))
        entries.clear()
        entries.addAll(FinanceStorage.loadEntries(appContext))
        currencyCode = FinanceStorage.loadCurrencyCode(appContext)
        themeMode = FinanceStorage.loadThemeMode(appContext).name
        hintsEnabled = FinanceStorage.loadHintsEnabled(appContext)
        autoBackupEnabled = FinanceStorage.loadAutoBackupEnabled(appContext)
        autoBackupMinutes = FinanceStorage.loadAutoBackupMinutes(appContext)
        autoBackupTreeUri = FinanceStorage.loadAutoBackupTreeUri(appContext)
        AutoBackupScheduler.sync(appContext)
        delay(1800)
        showTitleScreen = false
    }

    IvETheme(darkTheme = useDarkTheme, dynamicColor = false) {
        if (showTitleScreen) {
            TitleScreen()
        } else {
            FinanceTrackerScreen(
                categories = categories,
                entries = entries,
                onAddCategory = { name ->
                    val cleaned = name.trim()
                    val exists = categories.any { it.equals(cleaned, ignoreCase = true) }
                    when {
                        cleaned.isBlank() -> "Category name is required."
                        cleaned.length > MAX_CATEGORY_NAME_LENGTH -> "Category names can be up to $MAX_CATEGORY_NAME_LENGTH characters."
                        categories.size >= 30 -> "You can save up to 30 categories."
                        exists -> "That category already exists."
                        else -> {
                            categories.add(cleaned)
                            categories.sortBy { it.lowercase(Locale.getDefault()) }
                            FinanceStorage.saveCategories(appContext, categories)
                            null
                        }
                    }
                },
                onUpdateCategory = { oldName, newName ->
                    val cleaned = newName.trim()
                    val exists = categories.any {
                        !it.equals(oldName, ignoreCase = true) && it.equals(cleaned, ignoreCase = true)
                    }
                    when {
                        cleaned.isBlank() -> "Category name is required."
                        cleaned.length > MAX_CATEGORY_NAME_LENGTH -> "Category names can be up to $MAX_CATEGORY_NAME_LENGTH characters."
                        exists -> "That category already exists."
                        else -> {
                            val categoryIndex = categories.indexOfFirst { it == oldName }
                            if (categoryIndex >= 0) {
                                categories[categoryIndex] = cleaned
                                categories.sortBy { it.lowercase(Locale.getDefault()) }
                                entries.replaceAll { entry ->
                                    if (entry.category == oldName) entry.copy(category = cleaned) else entry
                                }
                                entries.sortByDescending { it.dateMillis }
                                FinanceStorage.saveCategories(appContext, categories)
                                FinanceStorage.saveEntries(appContext, entries)
                            }
                            null
                        }
                    }
                },
                onRemoveCategory = { category ->
                    when {
                        categories.size <= 1 -> "Keep at least one category available."
                        else -> {
                            categories.remove(category)
                            entries.removeAll { it.category == category }
                            FinanceStorage.saveCategories(appContext, categories)
                            FinanceStorage.saveEntries(appContext, entries)
                            null
                        }
                    }
                },
                currencyCode = currencyCode,
                onCurrencyCodeChange = { code ->
                    currencyCode = code
                    FinanceStorage.saveCurrencyCode(appContext, code)
                },
                themeMode = selectedThemeMode,
                onThemeModeChange = { mode ->
                    themeMode = mode.name
                    FinanceStorage.saveThemeMode(appContext, mode)
                },
                hintsEnabled = hintsEnabled,
                onHintsEnabledChange = { enabled ->
                    hintsEnabled = enabled
                    FinanceStorage.saveHintsEnabled(appContext, enabled)
                },
                autoBackupEnabled = autoBackupEnabled,
                onAutoBackupEnabledChange = { enabled ->
                    autoBackupEnabled = enabled
                    FinanceStorage.saveAutoBackupEnabled(appContext, enabled)
                    AutoBackupScheduler.sync(appContext)
                },
                autoBackupMinutes = autoBackupMinutes,
                onAutoBackupMinutesChange = { minutes ->
                    autoBackupMinutes = minutes
                    FinanceStorage.saveAutoBackupMinutes(appContext, minutes)
                    AutoBackupScheduler.sync(appContext)
                },
                autoBackupTreeUri = autoBackupTreeUri,
                onAutoBackupTreeUriChange = { treeUri ->
                    autoBackupTreeUri = treeUri
                    FinanceStorage.saveAutoBackupTreeUri(appContext, treeUri)
                    AutoBackupScheduler.sync(appContext)
                },
                onAddEntry = { entry ->
                    entries.add(0, entry)
                    FinanceStorage.saveEntries(appContext, entries)
                },
                onUpdateEntry = { updated ->
                    val index = entries.indexOfFirst { it.id == updated.id }
                    if (index >= 0) {
                        entries[index] = updated
                        entries.sortByDescending { it.dateMillis }
                        FinanceStorage.saveEntries(appContext, entries)
                    }
                },
                onDeleteEntry = { entryId ->
                    val index = entries.indexOfFirst { it.id == entryId }
                    if (index >= 0) {
                        entries.removeAt(index)
                        FinanceStorage.saveEntries(appContext, entries)
                    }
                },
                onReplaceBackup = { backupData ->
                    categories.clear()
                    categories.addAll(backupData.categories)
                    categories.sortBy { it.lowercase(Locale.getDefault()) }
                    entries.clear()
                    entries.addAll(backupData.entries.sortedByDescending { it.dateMillis })
                    currencyCode = backupData.currencyCode
                    themeMode = backupData.themeMode.name
                    hintsEnabled = backupData.hintsEnabled
                    FinanceStorage.saveCategories(appContext, categories)
                    FinanceStorage.saveEntries(appContext, entries)
                    FinanceStorage.saveCurrencyCode(appContext, currencyCode)
                    FinanceStorage.saveThemeMode(appContext, backupData.themeMode)
                    FinanceStorage.saveHintsEnabled(appContext, backupData.hintsEnabled)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceTrackerScreen(
    categories: List<String>,
    entries: List<FinanceEntry>,
    onAddCategory: (String) -> String?,
    onUpdateCategory: (String, String) -> String?,
    onRemoveCategory: (String) -> String?,
    currencyCode: String,
    onCurrencyCodeChange: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    hintsEnabled: Boolean,
    onHintsEnabledChange: (Boolean) -> Unit,
    autoBackupEnabled: Boolean,
    onAutoBackupEnabledChange: (Boolean) -> Unit,
    autoBackupMinutes: Int,
    onAutoBackupMinutesChange: (Int) -> Unit,
    autoBackupTreeUri: String?,
    onAutoBackupTreeUriChange: (String?) -> Unit,
    onAddEntry: (FinanceEntry) -> Unit,
    onUpdateEntry: (FinanceEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onReplaceBackup: (BackupData) -> Unit
) {
    val appContext = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val currentYear = calendar.get(Calendar.YEAR)
    val currentMonth = calendar.get(Calendar.MONTH)
    val scope = rememberCoroutineScope()

    var selectedType by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var selectedDateMillis by rememberSaveable { mutableStateOf(todayAtMidnight()) }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var newCategoryInput by rememberSaveable { mutableStateOf("") }
    var feedbackMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedYear by rememberSaveable { mutableStateOf(currentYear) }
    var selectedMonth by rememberSaveable { mutableStateOf(currentMonth) }
    var selectedFilterCategory by rememberSaveable { mutableStateOf("All Categories") }
    var transactionSearchQuery by rememberSaveable { mutableStateOf("") }
    var editingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCategoryDelete by rememberSaveable { mutableStateOf<PendingCategoryDelete?>(null) }
    var pendingTransaction by rememberSaveable { mutableStateOf<PendingTransaction?>(null) }
    var pendingImportBackup by remember { mutableStateOf<BackupData?>(null) }
    var screenMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                FinanceStorage.exportBackup(
                    context = appContext,
                    uri = uri,
                    backupData = BackupData(
                        categories = categories,
                        entries = entries,
                        currencyCode = currencyCode,
                        themeMode = themeMode,
                        hintsEnabled = hintsEnabled
                    )
                )
            }.onSuccess {
                feedbackMessage = "Backup exported successfully."
            }.onFailure {
                feedbackMessage = it.message ?: "Unable to export backup."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                FinanceStorage.importBackup(appContext, uri)
            }.onSuccess { backup ->
                pendingImportBackup = backup
            }.onFailure {
                feedbackMessage = it.message ?: "Unable to import backup."
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                onAutoBackupTreeUriChange(uri.toString())
            }.onSuccess {
                feedbackMessage = "Automatic backup folder saved."
            }.onFailure {
                feedbackMessage = it.message ?: "Unable to save the backup folder."
            }
        }
    }

    val availableSections = remember(hintsEnabled) {
        if (hintsEnabled) {
            AppSection.entries
        } else {
            AppSection.entries.filterNot { it == AppSection.HINTS }
        }
    }
    val swipeSections = availableSections
    val swipePagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { swipeSections.size }
    )
    val activeSection = swipeSections.getOrElse(swipePagerState.currentPage) { swipeSections.first() }
    val availableYears = (entries.map { millisToYear(it.dateMillis) } + currentYear)
        .distinct()
        .sortedDescending()
    LaunchedEffect(categories.size, categories.joinToString("|")) {
        if (categories.isNotEmpty() && (selectedCategory.isBlank() || selectedCategory !in categories)) {
            selectedCategory = categories.first()
        }
        if (selectedFilterCategory != "All Categories" && selectedFilterCategory !in categories) {
            selectedFilterCategory = "All Categories"
        }
    }

    LaunchedEffect(availableYears.joinToString("|")) {
        if (selectedYear !in availableYears) {
            selectedYear = availableYears.first()
        }
    }

    LaunchedEffect(hintsEnabled, activeSection) {
        if (!hintsEnabled && activeSection == AppSection.HINTS) {
            swipePagerState.scrollToPage(swipeSections.indexOf(AppSection.HOME))
        }
    }

    val matchesSelectedCategory: (FinanceEntry) -> Boolean = {
        selectedFilterCategory == "All Categories" || it.category == selectedFilterCategory
    }

    val selectedYearEntries = entries
        .filter { millisToYear(it.dateMillis) == selectedYear }
        .filter(matchesSelectedCategory)
    val filteredEntries = entries
        .filter { millisToYear(it.dateMillis) == selectedYear }
        .filter { selectedMonth == allYearMonth || millisToMonth(it.dateMillis) == selectedMonth }
        .filter(matchesSelectedCategory)
        .sortedByDescending { it.dateMillis }

    val sortedEntries = entries.sortedByDescending { it.dateMillis }
    val searchedEntries = if (transactionSearchQuery.isBlank()) {
        sortedEntries.take(10)
    } else {
        val query = transactionSearchQuery.trim().lowercase(Locale.getDefault())
        sortedEntries.filter { entry ->
            entry.category.lowercase(Locale.getDefault()).contains(query) ||
                formatDate(entry.dateMillis).lowercase(Locale.getDefault()).contains(query) ||
                formatCurrency(entry.amountCents, currencyCode).lowercase(Locale.getDefault()).contains(query) ||
                entry.type.name.lowercase(Locale.getDefault()).contains(query)
        }
    }

    val monthIncome = filteredEntries
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amountCents }
    val monthExpense = filteredEntries
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amountCents }
    val monthNet = monthIncome - monthExpense

    val yearIncome = selectedYearEntries
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amountCents }
    val yearExpense = selectedYearEntries
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amountCents }
    val currentYearEntries = entries.filter { millisToYear(it.dateMillis) == currentYear }
    val currentYearIncome = currentYearEntries
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amountCents }
    val currentYearExpense = currentYearEntries
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amountCents }

    val monthlyChartData = (0..11).map { month ->
        val monthEntries = entries.filter {
            millisToYear(it.dateMillis) == selectedYear && millisToMonth(it.dateMillis) == month
        }.filter(matchesSelectedCategory)
        MonthlyTotals(
            label = (month + 1).toString(),
            incomeCents = monthEntries.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
            expenseCents = monthEntries.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
        )
    }

    val yearlyChartData = availableYears.sorted().map { year ->
        val yearEntries = entries
            .filter { millisToYear(it.dateMillis) == year }
            .filter(matchesSelectedCategory)
        YearlyTotals(
            label = year.toString(),
            incomeCents = yearEntries.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
            expenseCents = yearEntries.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
        )
    }

    val categoryTotals = filteredEntries
        .groupBy { it.category }
        .mapValues { (_, items) ->
            items.fold(0L) { total, item ->
                total + if (item.type == TransactionType.INCOME) item.amountCents else -item.amountCents
            }
        }
        .toList()
        .sortedByDescending { (_, total) -> abs(total) }

    val editingEntry = entries.firstOrNull { it.id == editingEntryId }
    val pendingDeleteEntry = entries.firstOrNull { it.id == pendingDeleteEntryId }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    ScreenPickerTitle(
                        title = "I>E ${activeSection.title}",
                        sections = swipeSections,
                        selectedSection = activeSection,
                        expanded = screenMenuExpanded,
                        onExpandedChange = { screenMenuExpanded = it },
                        onSectionSelected = { section ->
                            scope.launch {
                                swipePagerState.animateScrollToPage(swipeSections.indexOf(section))
                            }
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = swipePagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val section = swipeSections[page]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (section) {
                        AppSection.HOME -> HomeSection(
                            categories = categories,
                            selectedType = selectedType,
                            onSelectedTypeChange = { selectedType = it },
                            selectedDateMillis = selectedDateMillis,
                            onSelectedDateMillisChange = { selectedDateMillis = it },
                            selectedCategory = selectedCategory,
                            onSelectedCategoryChange = { selectedCategory = it },
                            amountInput = amountInput,
                            onAmountInputChange = { amountInput = it },
                            currencyCode = currencyCode,
                            currentYear = currentYear,
                            currentYearIncome = currentYearIncome,
                            currentYearExpense = currentYearExpense,
                            onSaveEntry = {
                                val cents = parseAmountToMinorUnits(amountInput, currencyCode)
                                feedbackMessage = when {
                                    categories.isEmpty() -> "Create a category before saving transactions."
                                    selectedCategory.isBlank() -> "Select a category."
                                    cents == null || cents <= 0L -> "Enter a valid amount."
                                    else -> {
                                        pendingTransaction = PendingTransaction(
                                            type = selectedType,
                                            dateMillis = selectedDateMillis,
                                            category = selectedCategory,
                                            amountCents = cents
                                        )
                                        null
                                    }
                                }
                            },
                            appContext = appContext
                        )

                        AppSection.CATEGORIES -> CategoriesSection(
                            categories = categories,
                            newCategoryInput = newCategoryInput,
                            onNewCategoryInputChange = {
                                newCategoryInput = it.take(MAX_CATEGORY_NAME_LENGTH)
                            },
                            onAddCategory = {
                                feedbackMessage = onAddCategory(newCategoryInput)
                                    ?: "Category added: ${newCategoryInput.trim()}."
                                if (feedbackMessage?.startsWith("Category added:") == true) {
                                    if (selectedCategory.isBlank()) {
                                        selectedCategory = newCategoryInput.trim()
                                    }
                                    newCategoryInput = ""
                                }
                            },
                            onEditCategory = { category -> editingCategory = category },
                            onRemoveCategory = { category ->
                                pendingCategoryDelete = PendingCategoryDelete(
                                    category = category,
                                    transactionCount = entries.count { it.category == category }
                                )
                            }
                        )

                        AppSection.STATS -> StatsSection(
                            categories = categories,
                            availableYears = availableYears,
                            selectedYear = selectedYear,
                            onSelectedYearChange = { selectedYear = it },
                            selectedMonth = selectedMonth,
                            onSelectedMonthChange = { selectedMonth = it },
                            selectedFilterCategory = selectedFilterCategory,
                            onSelectedFilterCategoryChange = { selectedFilterCategory = it },
                            monthIncome = monthIncome,
                            monthExpense = monthExpense,
                            monthNet = monthNet,
                            yearIncome = yearIncome,
                            yearExpense = yearExpense,
                            yearNet = yearIncome - yearExpense,
                            currencyCode = currencyCode,
                            monthlyChartData = monthlyChartData,
                            yearlyChartData = yearlyChartData,
                            categoryTotals = categoryTotals
                        )

                        AppSection.TRANSACTIONS -> TransactionsSection(
                            transactionSearchQuery = transactionSearchQuery,
                            onTransactionSearchQueryChange = { transactionSearchQuery = it },
                            entries = searchedEntries,
                            isShowingSearchResults = transactionSearchQuery.isNotBlank(),
                            currencyCode = currencyCode,
                            onEditEntry = { entry -> editingEntryId = entry.id },
                            onDeleteEntry = { entry -> pendingDeleteEntryId = entry.id }
                        )

                        AppSection.HINTS -> HintsSection()

                        AppSection.SETTINGS -> SettingsSection(
                            selectedCurrencyCode = currencyCode,
                            onSelectedCurrencyCodeChange = {
                                onCurrencyCodeChange(it)
                                feedbackMessage = "Currency changed to $it."
                            },
                            selectedThemeMode = themeMode,
                            onSelectedThemeModeChange = {
                                onThemeModeChange(it)
                                feedbackMessage = "Theme changed to ${it.label}."
                            },
                            hintsEnabled = hintsEnabled,
                            onHintsEnabledChange = {
                                onHintsEnabledChange(it)
                                feedbackMessage = if (it) "Hints turned on." else "Hints turned off."
                            },
                            autoBackupEnabled = autoBackupEnabled,
                            onAutoBackupEnabledChange = { enabled ->
                                if (enabled && autoBackupTreeUri.isNullOrBlank()) {
                                    feedbackMessage = "Choose an automatic backup folder first."
                                } else {
                                    onAutoBackupEnabledChange(enabled)
                                    feedbackMessage = if (enabled) {
                                        "Automatic backup enabled."
                                    } else {
                                        "Automatic backup turned off."
                                    }
                                }
                            },
                            autoBackupMinutes = autoBackupMinutes,
                            onAutoBackupMinutesChange = { minutes ->
                                onAutoBackupMinutesChange(minutes)
                                feedbackMessage = "Automatic backup time updated."
                            },
                            autoBackupFolderLabel = autoBackupTreeUri
                                ?.let(::backupFolderLabelFromUri)
                                ?: "No backup folder selected.",
                            onChooseAutoBackupFolder = {
                                folderLauncher.launch(null)
                            },
                            onExportBackup = {
                                exportLauncher.launch("ive-backup-${backupFileDateStamp()}.json")
                            },
                            onImportBackup = {
                                importLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    }
                }
            }
        }
    }
    editingEntry?.let { entry ->
        EditTransactionDialog(
            categories = categories,
            entry = entry,
            currencyCode = currencyCode,
            onDismiss = { editingEntryId = null },
            onSave = { draft ->
                val cents = parseAmountToMinorUnits(draft.amountInput, currencyCode)
                feedbackMessage = when {
                    draft.category.isBlank() -> "Select a category."
                    cents == null || cents <= 0L -> "Enter a valid amount."
                    else -> {
                        onUpdateEntry(
                            entry.copy(
                                type = draft.type,
                                dateMillis = draft.dateMillis,
                                category = draft.category,
                                amountCents = cents
                            )
                        )
                        editingEntryId = null
                        "Transaction updated: ${draft.category}."
                    }
                }
            }
        )
    }

    pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteEntryId = null },
            title = { TitleWithAppIcon("Remove Transaction") },
            text = {
                Text("Remove ${formatCurrency(entry.amountCents, currencyCode)} from ${entry.category} on ${formatDate(entry.dateMillis)}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEntry(entry.id)
                        pendingDeleteEntryId = null
                        feedbackMessage = "Transaction removed: ${entry.category}."
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntryId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    editingCategory?.let { category ->
        EditCategoryDialog(
            currentName = category,
            onDismiss = { editingCategory = null },
            onSave = { newName ->
                feedbackMessage = onUpdateCategory(category, newName)
                    ?: "Category renamed: $category to ${newName.trim()}."
                if (feedbackMessage?.startsWith("Category renamed:") == true) {
                    if (selectedCategory == category) {
                        selectedCategory = newName.trim()
                    }
                    if (selectedFilterCategory == category) {
                        selectedFilterCategory = newName.trim()
                    }
                    editingCategory = null
                }
            }
        )
    }

    pendingCategoryDelete?.let { pendingDelete ->
        AlertDialog(
            onDismissRequest = { pendingCategoryDelete = null },
            title = { TitleWithAppIcon("Remove Category") },
            text = {
                Text(
                    "Removing ${pendingDelete.category} will also delete ${pendingDelete.transactionCount} related " +
                        if (pendingDelete.transactionCount == 1) "transaction." else "transactions."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        feedbackMessage = onRemoveCategory(pendingDelete.category)
                            ?: "Category removed: ${pendingDelete.category}. Deleted ${pendingDelete.transactionCount} related " +
                            if (pendingDelete.transactionCount == 1) "transaction." else "transactions."
                        pendingCategoryDelete = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingTransaction?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingTransaction = null },
            title = { TitleWithAppIcon("Save Transaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Review this transaction before saving.")
                    Text("Type: ${if (draft.type == TransactionType.INCOME) "Income" else "Expense"}")
                    Text("Date: ${formatDate(draft.dateMillis)}")
                    Text("Category: ${draft.category}")
                    Text("Amount: ${formatCurrency(draft.amountCents, currencyCode)}")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddEntry(
                            FinanceEntry(
                                id = UUID.randomUUID().toString(),
                                type = draft.type,
                                dateMillis = draft.dateMillis,
                                category = draft.category,
                                amountCents = draft.amountCents
                            )
                        )
                        pendingTransaction = null
                        amountInput = ""
                        feedbackMessage = "${draft.type.name.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }} saved for ${draft.category}."
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTransaction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingImportBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingImportBackup = null },
            title = { TitleWithAppIcon("Import Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Importing a backup will replace your current app data.")
                    Text("Categories: ${backup.categories.size}")
                    Text("Transactions: ${backup.entries.size}")
                    Text("Currency: ${backup.currencyCode}")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReplaceBackup(backup)
                        pendingImportBackup = null
                        feedbackMessage = "Backup imported successfully."
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportBackup = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TitleScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(1.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(MaterialTheme.typography.displayMedium.toSpanStyle().copy(color = positiveColor)) {
                        append("Income")
                    }
                    withStyle(MaterialTheme.typography.displayMedium.toSpanStyle().copy(color = Color.LightGray)) {
                        append(" > ")
                    }
                    withStyle(MaterialTheme.typography.displayMedium.toSpanStyle().copy(color = negativeColor)) {
                        append("Expense")
                    }
                },
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "It simple, make more than you spend!",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Track what comes in and what goes out.",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            }
            Text(
                text = createdByLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TitleWithAppIcon(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    fontWeight: FontWeight = FontWeight.SemiBold,
    iconSize: Dp = 18.dp,
    spacing: Dp = 8.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = text,
            style = style,
            fontWeight = fontWeight
        )
    }
}

@Composable
private fun ScreenPickerTitle(
    title: String,
    sections: List<AppSection>,
    selectedSection: AppSection,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSectionSelected: (AppSection) -> Unit
) {
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { onExpandedChange(true) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Choose screen",
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            sections.forEach { section ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = section.title,
                            fontWeight = if (section == selectedSection) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onExpandedChange(false)
                        onSectionSelected(section)
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeSection(
    categories: List<String>,
    selectedType: TransactionType,
    onSelectedTypeChange: (TransactionType) -> Unit,
    selectedDateMillis: Long,
    onSelectedDateMillisChange: (Long) -> Unit,
    selectedCategory: String,
    onSelectedCategoryChange: (String) -> Unit,
    amountInput: String,
    onAmountInputChange: (String) -> Unit,
    currencyCode: String,
    currentYear: Int,
    currentYearIncome: Long,
    currentYearExpense: Long,
    onSaveEntry: () -> Unit,
    appContext: Context
) {
    AddTransactionCard(
        categories = categories,
        selectedType = selectedType,
        onSelectedTypeChange = onSelectedTypeChange,
        selectedDateMillis = selectedDateMillis,
        onSelectedDateMillisChange = onSelectedDateMillisChange,
        selectedCategory = selectedCategory,
        onSelectedCategoryChange = onSelectedCategoryChange,
        amountInput = amountInput,
        onAmountInputChange = onAmountInputChange,
        currencyCode = currencyCode,
        onSaveEntry = onSaveEntry,
        appContext = appContext
    )

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitleWithAppIcon(
                text = "$currentYear Income > Expense",
                style = MaterialTheme.typography.titleLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryTile("Income", formatCurrency(currentYearIncome, currencyCode), positiveColor, Modifier.weight(1f))
                SummaryTile("Expense", formatCurrency(currentYearExpense, currencyCode), negativeColor, Modifier.weight(1f))
            }
            SummaryTile(
                label = "Net",
                value = formatSignedCurrency(currentYearIncome - currentYearExpense, currencyCode),
                accent = neutralColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HintsSection() {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitleWithAppIcon(
                text = "How to Use I>E",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "I>E is meant to stay simple. The goal is to record money coming in and money going out, then use the summaries to help keep income higher than expenses.",
                style = MaterialTheme.typography.bodyLarge
            )
            HintCard(
                title = "Start on Home",
                message = "Use Home to enter a new transaction quickly. Pick Income or Expense, choose the date and category, then type only numbers for the amount. I>E places the decimal automatically for the selected currency."
            )
            HintCard(
                title = "Save Carefully",
                message = "After entering a transaction, I>E shows a confirmation dialog with the details. Review the type, date, category, and amount, then choose Save or Cancel."
            )
            HintCard(
                title = "Manage Categories",
                message = "Use Categories to add, rename, or remove category names. If you rename a category, related transactions are updated. If you remove one, I>E warns you that matching transactions will also be deleted."
            )
            HintCard(
                title = "Read the Stats",
                message = "Use Stats to swipe between Summary, Month, Year, and Category views. Apply filters, switch between Chart and Values modes, and compare income against expenses without a long scrolling screen."
            )
            HintCard(
                title = "Review Transactions",
                message = "Use Transactions to search your full history, edit older entries, or remove them. When you are not searching, I>E shows the 10 most recent transactions to keep the screen clean."
            )
            HintCard(
                title = "Settings and Backups",
                message = "Use Settings to choose your currency, theme, and hint visibility. Export a backup file regularly so you can restore your categories, transactions, and preferences later."
            )
            HintCard(
                title = "Core Logic",
                message = "The idea behind the program is to keep tracking easy enough to do every time. Simple records are more useful than complicated records, and the main target is to keep total income above total expense over time."
            )
        }
    }
}

@Composable
private fun TransactionTypeSelector(
    selectedType: TransactionType,
    onSelectedTypeChange: (TransactionType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onSelectedTypeChange(TransactionType.INCOME) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedType == TransactionType.INCOME) positiveColor else inactiveTypeColor,
                contentColor = Color.White
            )
        ) {
            Text("Income")
        }
        Button(
            onClick = { onSelectedTypeChange(TransactionType.EXPENSE) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedType == TransactionType.EXPENSE) negativeColor else inactiveTypeColor,
                contentColor = Color.White
            )
        ) {
            Text("Expense")
        }
    }
}

@Composable
private fun CategoriesSection(
    categories: List<String>,
    newCategoryInput: String,
    onNewCategoryInputChange: (String) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (String) -> Unit,
    onRemoveCategory: (String) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitleWithAppIcon(
                text = "Categories (${categories.size}/30)",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = newCategoryInput,
                onValueChange = onNewCategoryInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New Category (Max $MAX_CATEGORY_NAME_LENGTH Chars)") },
                singleLine = true
            )

            Button(
                onClick = onAddCategory,
                enabled = categories.size < 30,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Category")
            }

            if (categories.isEmpty()) {
                Text("No categories yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                OutlinedButton(onClick = { onEditCategory(category) }) {
                                    Text("Edit Name")
                                }
                                TextButton(onClick = { onRemoveCategory(category) }) {
                                    Text("Remove")
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
private fun StatsSection(
    categories: List<String>,
    availableYears: List<Int>,
    selectedYear: Int,
    onSelectedYearChange: (Int) -> Unit,
    selectedMonth: Int,
    onSelectedMonthChange: (Int) -> Unit,
    selectedFilterCategory: String,
    onSelectedFilterCategoryChange: (String) -> Unit,
    monthIncome: Long,
    monthExpense: Long,
    monthNet: Long,
    yearIncome: Long,
    yearExpense: Long,
    yearNet: Long,
    currencyCode: String,
    monthlyChartData: List<MonthlyTotals>,
    yearlyChartData: List<YearlyTotals>,
    categoryTotals: List<Pair<String, Long>>
) {
    var filterCategoryMenuExpanded by remember { mutableStateOf(false) }
    var yearMenuExpanded by remember { mutableStateOf(false) }
    var monthMenuExpanded by remember { mutableStateOf(false) }
    var monthlyDisplayMode by rememberSaveable { mutableStateOf(StatsDisplayMode.GRAPH.name) }
    var yearlyDisplayMode by rememberSaveable { mutableStateOf(StatsDisplayMode.GRAPH.name) }
    var categoryDisplayMode by rememberSaveable { mutableStateOf(StatsDisplayMode.GRAPH.name) }
    val selectedMonthlyDisplayMode = StatsDisplayMode.valueOf(monthlyDisplayMode)
    val selectedYearlyDisplayMode = StatsDisplayMode.valueOf(yearlyDisplayMode)
    val selectedCategoryDisplayMode = StatsDisplayMode.valueOf(categoryDisplayMode)
    val periodLabel = if (selectedMonth == allYearMonth) "Year" else "Month"
    val categoryTotalsLabel = if (selectedMonth == allYearMonth) {
        "Category Totals ($selectedYear)"
    } else {
        "Category Totals (${monthName(selectedMonth)})"
    }
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitleWithAppIcon(
                text = "Filters and Summary",
                style = MaterialTheme.typography.titleLarge
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DropdownSelector(
                    label = "Year",
                    values = availableYears.map { it.toString() },
                    selectedValue = selectedYear.toString(),
                    expanded = yearMenuExpanded,
                    onExpandedChange = { yearMenuExpanded = it },
                    onValueSelected = {
                        onSelectedYearChange(it.toInt())
                        yearMenuExpanded = false
                    },
                    modifier = Modifier.weight(1f)
                )

                DropdownSelector(
                    label = "Month",
                    values = listOf(allYearLabel) + (0..11).map(::monthName),
                    selectedValue = monthSelectionLabel(selectedMonth),
                    expanded = monthMenuExpanded,
                    onExpandedChange = { monthMenuExpanded = it },
                    onValueSelected = { picked ->
                        onSelectedMonthChange(monthIndexFromNameOrAllYear(picked))
                        monthMenuExpanded = false
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Box {
                OutlinedButton(
                    onClick = { filterCategoryMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedFilterCategory)
                }
                DropdownMenu(
                    expanded = filterCategoryMenuExpanded,
                    onDismissRequest = { filterCategoryMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Categories") },
                        onClick = {
                            onSelectedFilterCategoryChange("All Categories")
                            filterCategoryMenuExpanded = false
                        }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                onSelectedFilterCategoryChange(category)
                                filterCategoryMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryTile("$periodLabel Income", formatCurrency(monthIncome, currencyCode), positiveColor, Modifier.weight(1f))
                SummaryTile("$periodLabel Expense", formatCurrency(monthExpense, currencyCode), negativeColor, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryTile("$periodLabel Net", formatSignedCurrency(monthNet, currencyCode), neutralColor, Modifier.weight(1f))
                SummaryTile("Year Net", formatSignedCurrency(yearNet, currencyCode), neutralColor, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryTile("Year Income", formatCurrency(yearIncome, currencyCode), positiveColor, Modifier.weight(1f))
                SummaryTile("Year Expense", formatCurrency(yearExpense, currencyCode), negativeColor, Modifier.weight(1f))
            }
        }
    }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitleWithAppIcon(
                text = "Monthly Chart ($selectedYear)",
                style = MaterialTheme.typography.titleLarge
            )
            StatsDisplayModeSelector(
                selectedMode = selectedMonthlyDisplayMode,
                onModeSelected = { monthlyDisplayMode = it.name }
            )
            if (selectedMonthlyDisplayMode == StatsDisplayMode.GRAPH) {
                DualBarChart(
                    monthlyData = monthlyChartData.map {
                        ChartBarGroup(it.label, it.incomeCents, it.expenseCents)
                    }
                )
            } else {
                MonthlyStatsValueList(
                    items = monthlyChartData.map {
                        Triple(
                            monthName(it.label.toInt() - 1),
                            formatCurrency(it.expenseCents, currencyCode),
                            formatCurrency(it.incomeCents, currencyCode)
                        )
                    }
                )
            }
        }
    }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitleWithAppIcon(
                text = "Yearly Chart",
                style = MaterialTheme.typography.titleLarge
            )
            StatsDisplayModeSelector(
                selectedMode = selectedYearlyDisplayMode,
                onModeSelected = { yearlyDisplayMode = it.name }
            )
            if (selectedYearlyDisplayMode == StatsDisplayMode.GRAPH) {
                DualBarChart(
                    monthlyData = yearlyChartData.map {
                        ChartBarGroup(it.label, it.incomeCents, it.expenseCents)
                    }
                )
            } else {
                MonthlyStatsValueList(
                    items = yearlyChartData.map {
                        Triple(
                            it.label,
                            formatCurrency(it.expenseCents, currencyCode),
                            formatCurrency(it.incomeCents, currencyCode)
                        )
                    }
                )
            }
        }
    }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TitleWithAppIcon(
                text = categoryTotalsLabel,
                style = MaterialTheme.typography.titleLarge
            )
            StatsDisplayModeSelector(
                selectedMode = selectedCategoryDisplayMode,
                onModeSelected = { categoryDisplayMode = it.name }
            )
            if (categoryTotals.isEmpty()) {
                Text(if (selectedMonth == allYearMonth) "No transactions found for the selected year." else "No transactions found for the selected month.")
            } else if (selectedCategoryDisplayMode == StatsDisplayMode.GRAPH) {
                CategoryTotalsBarChart(
                    categoryTotals = categoryTotals,
                    currencyCode = currencyCode
                )
            } else {
                CategoryTotalsValueList(
                    categoryTotals = categoryTotals,
                    currencyCode = currencyCode
                )
            }
        }
    }
}

@Composable
private fun TransactionsSection(
    transactionSearchQuery: String,
    onTransactionSearchQueryChange: (String) -> Unit,
    entries: List<FinanceEntry>,
    isShowingSearchResults: Boolean,
    currencyCode: String,
    onEditEntry: (FinanceEntry) -> Unit,
    onDeleteEntry: (FinanceEntry) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitleWithAppIcon(
                text = if (isShowingSearchResults) "Search Results" else "Recent Transactions",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = transactionSearchQuery,
                onValueChange = onTransactionSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search Transactions") },
                placeholder = { Text("Search by category, date, type, or amount") },
                singleLine = true
            )

            if (entries.isEmpty()) {
                Text(if (isShowingSearchResults) "No transactions match your search." else "No transactions yet.")
            } else {
                if (!isShowingSearchResults) {
                    Text(
                        text = "Showing the 10 most recent transactions. Use Search to find older entries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    entries.forEach { entry ->
                        TransactionRow(
                            entry = entry,
                            currencyCode = currencyCode,
                            onEdit = { onEditEntry(entry) },
                            onDelete = { onDeleteEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditTransactionDialog(
    categories: List<String>,
    entry: FinanceEntry,
    currencyCode: String,
    onDismiss: () -> Unit,
    onSave: (TransactionDraft) -> Unit
) {
    var selectedType by remember(entry.id) { mutableStateOf(entry.type) }
    var selectedDateMillis by remember(entry.id) { mutableStateOf(entry.dateMillis) }
    var selectedCategory by remember(entry.id) { mutableStateOf(entry.category) }
    var amountInput by remember(entry.id) {
        mutableStateOf(formatMinorUnitsForInput(entry.amountCents, currencyCode))
    }
    var categoryMenuExpanded by remember(entry.id) { mutableStateOf(false) }
    val appContext = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TitleWithAppIcon("Edit Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TransactionTypeSelector(
                    selectedType = selectedType,
                    onSelectedTypeChange = { selectedType = it }
                )

                OutlinedButton(
                    onClick = {
                        showDatePicker(
                            pickerContext = appContext,
                            initialMillis = selectedDateMillis
                        ) { pickedMillis ->
                            selectedDateMillis = pickedMillis
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Date: ${formatDate(selectedDateMillis)}")
                }

                Box {
                    OutlinedButton(
                        onClick = { categoryMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = categories.isNotEmpty()
                    ) {
                        Text(
                            text = if (selectedCategory.isBlank()) {
                                "Select Category"
                            } else {
                                "Category: $selectedCategory"
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = normalizeAmountInput(it, currencyCode) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount ($currencyCode)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        TransactionDraft(
                            type = selectedType,
                            dateMillis = selectedDateMillis,
                            category = selectedCategory,
                            amountInput = amountInput
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditCategoryDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var categoryName by remember(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TitleWithAppIcon("Edit Category") },
        text = {
            OutlinedTextField(
                value = categoryName,
                onValueChange = { categoryName = it.take(MAX_CATEGORY_NAME_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Category name (max $MAX_CATEGORY_NAME_LENGTH)") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onSave(categoryName) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SettingsSection(
    selectedCurrencyCode: String,
    onSelectedCurrencyCodeChange: (String) -> Unit,
    selectedThemeMode: ThemeMode,
    onSelectedThemeModeChange: (ThemeMode) -> Unit,
    hintsEnabled: Boolean,
    onHintsEnabledChange: (Boolean) -> Unit,
    autoBackupEnabled: Boolean,
    onAutoBackupEnabledChange: (Boolean) -> Unit,
    autoBackupMinutes: Int,
    onAutoBackupMinutesChange: (Int) -> Unit,
    autoBackupFolderLabel: String,
    onChooseAutoBackupFolder: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val context = LocalContext.current
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }
    val selectedCurrency = commonCurrencies.firstOrNull { it.code == selectedCurrencyCode }
        ?: commonCurrencies.first()

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitleWithAppIcon(
                text = "Currency",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Choose the currency used for summaries, charts, and transaction amounts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box {
                OutlinedButton(
                    onClick = { currencyMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedCurrency.label)
                }
                DropdownMenu(
                    expanded = currencyMenuExpanded,
                    onDismissRequest = { currencyMenuExpanded = false },
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    commonCurrencies.forEach { currency ->
                        DropdownMenuItem(
                            text = { Text(currency.label) },
                            onClick = {
                                onSelectedCurrencyCodeChange(currency.code)
                                currencyMenuExpanded = false
                            }
                        )
                    }
                }
            }

            TitleWithAppIcon(
                text = "Background",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Choose whether the app uses a light background, dark background, or the system setting.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                OutlinedButton(
                    onClick = { themeMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedThemeMode.label)
                }
                DropdownMenu(
                    expanded = themeMenuExpanded,
                    onDismissRequest = { themeMenuExpanded = false }
                ) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                onSelectedThemeModeChange(mode)
                                themeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            TitleWithAppIcon(
                text = "Backup",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Export your data to a file or import a previous backup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Automatic Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Create one backup file every day at ${formatTimeOfDay(autoBackupMinutes)}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = onAutoBackupEnabledChange
                )
            }
            OutlinedButton(
                onClick = {
                    val hour = autoBackupMinutes / 60
                    val minute = autoBackupMinutes % 60
                    TimePickerDialog(
                        context,
                        { _, pickedHour, pickedMinute ->
                            onAutoBackupMinutesChange((pickedHour * 60) + pickedMinute)
                        },
                        hour,
                        minute,
                        false
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Auto Backup Time: ${formatTimeOfDay(autoBackupMinutes)}")
            }
            OutlinedButton(
                onClick = onChooseAutoBackupFolder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose Auto Backup Folder")
            }
            Text(
                text = autoBackupFolderLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onExportBackup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Backup")
            }
            OutlinedButton(
                onClick = onImportBackup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Backup")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TitleWithAppIcon(
                text = "About",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "How to Use I>E",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "1. Start on Home and add categories if you need more than the defaults.\n2. Enter transaction amounts by typing digits only; the decimal is placed automatically.\n3. Review the confirmation dialog before saving a transaction.\n4. Use Stats to filter by year, month, or category, then switch between Chart and Values views.\n5. Use Transactions to search, edit, or remove saved entries.\n6. Open the Hints page from the menu for quick guidance on using the app.\n7. Export a backup from Settings before switching devices or clearing app data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DualBarChart(monthlyData: List<ChartBarGroup>) {
    val maxValue = monthlyData.maxOfOrNull { maxOf(it.incomeCents, it.expenseCents) }?.coerceAtLeast(1L) ?: 1L

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            monthlyData.forEach { item ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        ChartBar(
                            value = item.incomeCents,
                            maxValue = maxValue,
                            color = positiveColor,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(4.dp))
                        ChartBar(
                            value = item.expenseCents,
                            maxValue = maxValue,
                            color = negativeColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = item.label,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot("Income", positiveColor)
        LegendDot("Expense", negativeColor)
    }
}

@Composable
private fun CategoryTotalsBarChart(
    categoryTotals: List<Pair<String, Long>>,
    currencyCode: String
) {
    val maxValue = categoryTotals.maxOfOrNull { (_, total) -> abs(total) }?.coerceAtLeast(1L) ?: 1L

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                categoryTotals.forEach { (category, total) ->
                    val barColor = if (total >= 0) positiveColor else negativeColor
                    Column(
                        modifier = Modifier.width(72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = formatSignedCurrency(total, currencyCode),
                            color = barColor,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ChartBar(
                            value = abs(total),
                            maxValue = maxValue,
                            color = barColor,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = category,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot("Net income", positiveColor)
            LegendDot("Net expense", negativeColor)
        }
    }
}

@Composable
private fun HintCard(
    title: String,
    message: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun StatsValueList(
    modifier: Modifier = Modifier,
    items: List<Triple<String, String, String>>,
    startLabel: String,
    endLabel: String,
    startValueColor: Color = MaterialTheme.colorScheme.onSurface,
    endValueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { (label, startValue, endValue) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$startLabel: $startValue",
                            color = startValueColor
                        )
                        Text(
                            text = "$endLabel: $endValue",
                            color = endValueColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyStatsValueList(
    modifier: Modifier = Modifier,
    items: List<Triple<String, String, String>>
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { (label, expenseValue, incomeValue) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Expense: $expenseValue",
                        color = negativeColor
                    )
                    Text(
                        text = "Income: $incomeValue",
                        color = positiveColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTotalsValueList(
    modifier: Modifier = Modifier,
    categoryTotals: List<Pair<String, Long>>,
    currencyCode: String
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categoryTotals.forEach { (category, total) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(category, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = formatSignedCurrency(total, currencyCode),
                        color = if (total >= 0) positiveColor else negativeColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsDisplayModeSelector(
    selectedMode: StatsDisplayMode,
    onModeSelected: (StatsDisplayMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsDisplayMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode.label) }
            )
        }
    }
}

@Composable
private fun ChartBar(
    value: Long,
    maxValue: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    val fraction = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((160f * fraction).dp.coerceAtLeast(6.dp))
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(color)
        )
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TransactionRow(
    entry: FinanceEntry,
    currencyCode: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.category, fontWeight = FontWeight.Medium)
                    Text(
                        text = formatDate(entry.dateMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (entry.type == TransactionType.INCOME) "Income" else "Expense",
                    color = if (entry.type == TransactionType.INCOME) positiveColor else negativeColor,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    text = formatCurrency(entry.amountCents, currencyCode),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawRoundRect(color = accent, cornerRadius = CornerRadius(99f, 99f))
                }
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AddTransactionCard(
    categories: List<String>,
    selectedType: TransactionType,
    onSelectedTypeChange: (TransactionType) -> Unit,
    selectedDateMillis: Long,
    onSelectedDateMillisChange: (Long) -> Unit,
    selectedCategory: String,
    onSelectedCategoryChange: (String) -> Unit,
    amountInput: String,
    onAmountInputChange: (String) -> Unit,
    currencyCode: String,
    onSaveEntry: () -> Unit,
    appContext: Context
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var amountFieldValue by remember {
        mutableStateOf(TextFieldValue(amountInput, TextRange(amountInput.length)))
    }

    LaunchedEffect(amountInput) {
        if (amountFieldValue.text != amountInput) {
            amountFieldValue = TextFieldValue(amountInput, TextRange(amountInput.length))
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitleWithAppIcon(
                text = "Add Transaction",
                style = MaterialTheme.typography.titleLarge
            )

            TransactionTypeSelector(
                selectedType = selectedType,
                onSelectedTypeChange = onSelectedTypeChange
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        showDatePicker(
                            pickerContext = appContext,
                            initialMillis = selectedDateMillis,
                            onDatePicked = onSelectedDateMillisChange
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Date: ${formatDate(selectedDateMillis)}")
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { categoryMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = categories.isNotEmpty()
                    ) {
                        Text(
                            text = if (selectedCategory.isBlank()) {
                                "Select Category"
                            } else {
                                "Category: $selectedCategory"
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    onSelectedCategoryChange(category)
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = amountFieldValue,
                onValueChange = { updatedValue ->
                    val normalizedValue = normalizeAmountInput(updatedValue.text, currencyCode)
                    amountFieldValue = TextFieldValue(
                        text = normalizedValue,
                        selection = TextRange(normalizedValue.length)
                    )
                    onAmountInputChange(normalizedValue)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount ($currencyCode)") },
                placeholder = { Text(defaultAmountPlaceholder(currencyCode)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Button(
                onClick = onSaveEntry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Transaction")
            }
        }
    }
}

@Composable
private fun DropdownSelector(
    label: String,
    values: List<String>,
    selectedValue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { onExpandedChange(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedValue)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
            values.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(value) },
                        onClick = { onValueSelected(value) }
                    )
                }
            }
        }
    }
}

private fun showDatePicker(
    pickerContext: Context,
    initialMillis: Long,
    onDatePicked: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        pickerContext,
        { _, year, month, dayOfMonth ->
            val picked = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onDatePicked(picked.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun parseAmountToMinorUnits(value: String, currencyCode: String): Long? {
    val normalized = value.trim().replace(",", "")
    val number = normalized.toDoubleOrNull() ?: return null
    val fractionDigits = currencyFractionDigits(currencyCode)
    val multiplier = 10.0.pow(fractionDigits.toDouble())
    return (number * multiplier).roundToInt().toLong()
}

private fun normalizeAmountInput(value: String, currencyCode: String): String {
    val digits = value.filter(Char::isDigit)
    if (digits.isEmpty()) return ""

    val fractionDigits = currencyFractionDigits(currencyCode)
    if (fractionDigits == 0) {
        return formatGroupedWholeNumber(digits.trimStart('0').ifEmpty { "0" })
    }

    val padded = digits.padStart(fractionDigits + 1, '0')
    val wholePart = padded.dropLast(fractionDigits).trimStart('0').ifEmpty { "0" }
    val decimalPart = padded.takeLast(fractionDigits)
    return "${formatGroupedWholeNumber(wholePart)}.$decimalPart"
}

private fun formatMinorUnitsForInput(amountMinorUnits: Long, currencyCode: String): String {
    val fractionDigits = currencyFractionDigits(currencyCode)
    if (fractionDigits == 0) {
        return formatGroupedWholeNumber(amountMinorUnits.toString())
    }

    val digits = amountMinorUnits.toString().padStart(fractionDigits + 1, '0')
    val wholePart = digits.dropLast(fractionDigits).trimStart('0').ifEmpty { "0" }
    val decimalPart = digits.takeLast(fractionDigits)
    return "${formatGroupedWholeNumber(wholePart)}.$decimalPart"
}

private fun defaultAmountPlaceholder(currencyCode: String): String {
    val fractionDigits = currencyFractionDigits(currencyCode)
    return if (fractionDigits == 0) "0" else buildString {
        append("0.")
        repeat(fractionDigits) { append('0') }
    }
}

private fun formatGroupedWholeNumber(value: String): String {
    val digitsOnly = value.filter(Char::isDigit)
    if (digitsOnly.isEmpty()) return "0"
    return digitsOnly
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
}

private fun formatDate(dateMillis: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(dateMillis)
}

private fun formatCurrency(amountCents: Long, currencyCode: String): String {
    val currency = Currency.getInstance(currencyCode)
    val fractionDigits = currencyFractionDigits(currencyCode)
    return NumberFormat.getCurrencyInstance().apply {
        this.currency = currency
        maximumFractionDigits = fractionDigits
        minimumFractionDigits = fractionDigits
    }.format(amountCents / 10.0.pow(fractionDigits.toDouble()))
}

private fun formatSignedCurrency(amountCents: Long, currencyCode: String): String {
    val prefix = if (amountCents >= 0) "+" else "-"
    return prefix + formatCurrency(abs(amountCents), currencyCode).replace("-", "")
}

private fun formatAbbreviatedCurrency(amountCents: Long, currencyCode: String): String {
    val currency = Currency.getInstance(currencyCode)
    val fractionDigits = currencyFractionDigits(currencyCode)
    val amount = amountCents / 10.0.pow(fractionDigits.toDouble())
    val absoluteAmount = abs(amount)

    val (scaledAmount, suffix) = when {
        absoluteAmount >= 1_000_000_000 -> amount / 1_000_000_000 to "B"
        absoluteAmount >= 1_000_000 -> amount / 1_000_000 to "M"
        absoluteAmount >= 1_000 -> amount / 1_000 to "K"
        else -> amount to ""
    }

    val formattedNumber = if (suffix.isEmpty()) {
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }.format(scaledAmount)
    } else {
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(scaledAmount)
    }

    return "${currency.symbol}$formattedNumber$suffix"
}

private fun currencyFractionDigits(currencyCode: String): Int {
    return Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
}

private fun backupFileDateStamp(): String {
    return SimpleDateFormat("yyyyMMdd", Locale.US).format(System.currentTimeMillis())
}

private fun backupFileTimestamp(): String {
    return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis())
}

private fun nextAutoBackupTriggerAt(minutesAfterMidnight: Int): Long {
    val now = Calendar.getInstance()
    val next = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutesAfterMidnight / 60)
        set(Calendar.MINUTE, minutesAfterMidnight % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (!after(now)) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    return next.timeInMillis
}

private fun formatTimeOfDay(minutesAfterMidnight: Int): String {
    val hour = (minutesAfterMidnight / 60).coerceIn(0, 23)
    val minute = (minutesAfterMidnight % 60).coerceIn(0, 59)
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
}

private fun backupFolderLabelFromUri(uriString: String): String {
    return runCatching {
        val treeUri = Uri.parse(uriString)
        DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast(':')
    }.getOrElse { "Selected backup folder." }
}

private fun createdByLabel(): String {
    return "Created by tazrog © 2026."
}

private fun millisToYear(dateMillis: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = dateMillis }.get(Calendar.YEAR)
}

private fun millisToMonth(dateMillis: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = dateMillis }.get(Calendar.MONTH)
}

private fun todayAtMidnight(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun monthName(month: Int): String {
    return Calendar.getInstance().apply {
        set(Calendar.MONTH, month)
    }.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).orEmpty()
}

private fun monthIndexFromName(name: String): Int {
    return (0..11).firstOrNull { monthName(it) == name } ?: 0
}

private const val allYearLabel = "All Year"

private fun monthSelectionLabel(month: Int): String {
    return if (month == allYearMonth) allYearLabel else monthName(month)
}

private fun monthIndexFromNameOrAllYear(name: String): Int {
    return if (name == allYearLabel) allYearMonth else monthIndexFromName(name)
}

private val positiveColor = Color(0xFF1B8A5A)
private val negativeColor = Color(0xFFBF3B3B)
private val neutralColor = Color(0xFF2762D8)
private val inactiveTypeColor = Color(0xFF7A7A7A)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun FinanceTrackerPreview() {
    IvETheme {
        FinanceTrackerScreen(
            categories = listOf("Salary", "Food", "Travel"),
            entries = listOf(
                FinanceEntry(
                    id = "1",
                    type = TransactionType.INCOME,
                    dateMillis = todayAtMidnight(),
                    category = "Salary",
                    amountCents = 280000
                ),
                FinanceEntry(
                    id = "2",
                    type = TransactionType.EXPENSE,
                    dateMillis = todayAtMidnight(),
                    category = "Food",
                    amountCents = 2599
                )
            ),
            onAddCategory = { null },
            onUpdateCategory = { _, _ -> null },
            onRemoveCategory = { null },
            currencyCode = defaultCurrencyCode,
            onCurrencyCodeChange = {},
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {},
            hintsEnabled = true,
            onHintsEnabledChange = {},
            autoBackupEnabled = false,
            onAutoBackupEnabledChange = {},
            autoBackupMinutes = 120,
            onAutoBackupMinutesChange = {},
            autoBackupTreeUri = null,
            onAutoBackupTreeUriChange = {},
            onAddEntry = {},
            onUpdateEntry = {},
            onDeleteEntry = {},
            onReplaceBackup = {}
        )
    }
}
