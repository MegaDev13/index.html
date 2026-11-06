package dev.neonos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews

class TerminalWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val theme = Themes.current(context)
        val display = RemoteViews(context.packageName, R.layout.terminal_widget)
        display.setTextColor(R.id.terminal_log, theme.accent)
        display.setTextViewText(R.id.terminal_log, EventJournal.lines(3).ifBlank { "Toque para abrir o console local." })
        display.setOnClickPendingIntent(R.id.terminal_widget, PendingIntent.getActivity(context, 41,
            Intent(context, TerminalActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        manager.updateAppWidget(ids, display)
    }

    companion object {
        private var lastRefresh = 0L
        fun refresh(context: Context) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRefresh < 5000) return
            lastRefresh = now
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TerminalWidget::class.java))
            if (ids.isNotEmpty()) TerminalWidget().onUpdate(context, manager, ids)
        }
    }
}
