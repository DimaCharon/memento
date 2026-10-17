package ru.memento.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.memento.R
import ru.memento.data.Ru
import ru.memento.data.Store

/** Виджет «Быстрое добавление»: одна кнопка «+» и счётчик дел на сегодня. */
class QuickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "ru.memento.widget.REFRESH_QUICK"

        /** Собирает карточку виджета: используется и на телефоне, и в тестах/превью. */
        fun build(context: Context): RemoteViews {
            val store = Store.get(context)
            val left = store.today().count { !it.done }
            val v = RemoteViews(context.packageName, R.layout.widget_quick)
            v.setTextViewText(
                R.id.quick_count,
                if (left == 0) context.getString(R.string.nothing_today)
                else "${Ru.eventsWord(left)} на сегодня",
            )
            v.setOnClickPendingIntent(R.id.widget_quick_root, WidgetIntents.newReminder(context, 80))
            return v
        }

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, build(context))
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            Widgets.ids(context, QuickWidgetProvider::class.java).forEach { update(context, manager, it) }
        }
    }
}
