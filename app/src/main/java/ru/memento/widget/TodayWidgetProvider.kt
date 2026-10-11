package ru.memento.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import ru.memento.R
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.ui.MainActivity
import ru.memento.ui.ReminderSheetActivity

/** Виджет «Сегодня»: список дел на день прямо на рабочем столе. */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "ru.memento.widget.REFRESH_TODAY"

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val store = Store.get(context)
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val today = store.today()
            val left = today.count { !it.done }

            views.setTextViewText(
                R.id.widget_count,
                if (today.isEmpty()) context.getString(R.string.no_reminders) else Ru.eventsWord(left),
            )
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            val serviceIntent = Intent(context, TodayWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = android.net.Uri.parse("memento://today/$id")
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)

            // Тап по строке — открыть напоминание, тап по галочке — отметить выполненным
            val template = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, WidgetActionReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.widget_list, template)

            val open = PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val add = PendingIntent.getActivity(
                context, 11,
                Intent(context, ReminderSheetActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    action = ReminderSheetActivity.ACTION_NEW
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_plus, add)
            views.setViewVisibility(R.id.widget_plus, View.VISIBLE)

            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
            manager.updateAppWidget(id, views)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }
    }
}
