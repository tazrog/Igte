package com.tazrog.ive

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class AddTransactionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val addTransactionPendingIntent = PendingIntent.getActivity(
            context,
            1,
            addTransactionWidgetIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.add_transaction_widget).apply {
                setOnClickPendingIntent(R.id.add_transaction_widget_root, addTransactionPendingIntent)
                setOnClickPendingIntent(R.id.add_transaction_widget_bubble, addTransactionPendingIntent)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
