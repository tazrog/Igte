package com.tazrog.ive

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Calendar
import java.util.Currency
import kotlin.math.abs
import kotlin.math.pow

class YearIncomeExpenseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateYearIncomeExpenseWidgets(context, appWidgetManager, appWidgetIds)
    }
}

internal object YearIncomeExpenseWidgetUpdater {
    fun updateAll(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, YearIncomeExpenseWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isNotEmpty()) {
            updateYearIncomeExpenseWidgets(context, appWidgetManager, appWidgetIds)
        }
    }
}

private const val widgetPrefsName = "finance_tracker"
private const val widgetEntriesKey = "entries"
private const val widgetCurrencyKey = "currency"
private const val widgetDefaultCurrencyCode = "USD"

private fun updateYearIncomeExpenseWidgets(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
) {
    val summary = loadCurrentYearSummary(context)
    val openAppIntent = Intent(context, MainActivity::class.java)
    val openAppPendingIntent = PendingIntent.getActivity(
        context,
        0,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    appWidgetIds.forEach { appWidgetId ->
        val views = RemoteViews(context.packageName, R.layout.year_income_expense_widget).apply {
            setTextViewText(
                R.id.widget_title,
                context.getString(R.string.year_income_expense_widget_title, summary.year)
            )
            setTextViewText(R.id.widget_value, formatSignedCurrency(summary.netAmountCents, summary.currencyCode))
            setViewVisibility(
                R.id.widget_empty,
                if (summary.hasEntries) View.GONE else View.VISIBLE
            )
            setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

private data class WidgetSummary(
    val year: Int,
    val currencyCode: String,
    val netAmountCents: Long,
    val hasEntries: Boolean
)

private fun loadCurrentYearSummary(context: Context): WidgetSummary {
    val prefs = context.getSharedPreferences(widgetPrefsName, Context.MODE_PRIVATE)
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val currencyCode = prefs.getString(widgetCurrencyKey, widgetDefaultCurrencyCode)
        ?: widgetDefaultCurrencyCode
    val entriesJson = prefs.getString(widgetEntriesKey, null)
    if (entriesJson.isNullOrBlank()) {
        return WidgetSummary(
            year = currentYear,
            currencyCode = currencyCode,
            netAmountCents = 0L,
            hasEntries = false
        )
    }

    var incomeCents = 0L
    var expenseCents = 0L
    val entries = JSONArray(entriesJson)
    for (index in 0 until entries.length()) {
        val item = entries.getJSONObject(index)
        if (millisToYear(item.getLong("dateMillis")) != currentYear) continue
        when (item.getString("type")) {
            "INCOME" -> incomeCents += item.getLong("amountCents")
            "EXPENSE" -> expenseCents += item.getLong("amountCents")
        }
    }

    return WidgetSummary(
        year = currentYear,
        currencyCode = currencyCode,
        netAmountCents = incomeCents - expenseCents,
        hasEntries = incomeCents != 0L || expenseCents != 0L
    )
}

private fun formatSignedCurrency(amountCents: Long, currencyCode: String): String {
    val prefix = if (amountCents >= 0) "+" else "-"
    return prefix + formatCurrency(abs(amountCents), currencyCode).replace("-", "")
}

private fun formatCurrency(amountCents: Long, currencyCode: String): String {
    val currency = Currency.getInstance(currencyCode)
    val fractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
    return NumberFormat.getCurrencyInstance().apply {
        this.currency = currency
        maximumFractionDigits = fractionDigits
        minimumFractionDigits = fractionDigits
    }.format(amountCents / 10.0.pow(fractionDigits.toDouble()))
}

private fun millisToYear(dateMillis: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = dateMillis }.get(Calendar.YEAR)
}
