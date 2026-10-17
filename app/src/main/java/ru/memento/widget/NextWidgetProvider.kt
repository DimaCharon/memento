package ru.memento.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.memento.R
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.data.dotDrawableRes

/** Виджет «Следующее»: ближайшее дело крупным планом. */
class NextWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "ru.memento.widget.REFRESH_NEXT"

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val store = Store.get(context)
            val r = store.next()
            val v = RemoteViews(context.packageName, R.layout.widget_next)

            if (r == null) {
                v.setTextViewText(R.id.next_time, "--:--")
                v.setTextViewText(R.id.next_title, context.getString(R.string.next_none))
                v.setTextViewText(R.id.next_place, "")
                v.setImageViewResource(R.id.next_dot, R.drawable.dot_low)
                v.setOnClickPendingIntent(R.id.widget_next_root, WidgetIntents.openApp(context, 31))
            } else {
                v.setTextViewText(R.id.next_time, r.time)
                v.setTextViewText(R.id.next_title, r.title)
                v.setTextViewText(R.id.next_place, r.place.ifBlank { Ru.dayLabel(r.date) })
                v.setImageViewResource(R.id.next_dot, r.priority.dotDrawableRes())
                v.setOnClickPendingIntent(R.id.widget_next_root, WidgetIntents.openReminder(context, 31, r.id))
            }
            manager.updateAppWidget(id, v)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            Widgets.ids(context, NextWidgetProvider::class.java).forEach { update(context, manager, it) }
        }
    }
}
