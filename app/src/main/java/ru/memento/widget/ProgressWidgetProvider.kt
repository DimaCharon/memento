package ru.memento.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.memento.R
import ru.memento.data.Store
import ru.memento.ui.MainActivity

/** Виджет «Прогресс дня»: кольцо и число выполненных дел. */
class ProgressWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "ru.memento.widget.REFRESH_PROGRESS"

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val store = Store.get(context)
            val (done, total) = store.todayStats()
            val views = RemoteViews(context.packageName, R.layout.widget_progress)
            views.setTextViewText(R.id.progress_done, done.toString())
            views.setTextViewText(R.id.progress_total, total.toString())
            val percent = if (total > 0) (done * 100) / total else 0
            views.setProgressBar(R.id.progress_ring, 100, percent, false)

            val open = PendingIntent.getActivity(
                context, 20,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_progress_root, open)
            manager.updateAppWidget(id, views)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ProgressWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }
    }
}
