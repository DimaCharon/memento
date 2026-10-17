package ru.memento.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import ru.memento.R
import ru.memento.data.Ru
import ru.memento.data.Store

/**
 * Виджет «Расписание на день»: до пяти дел с галочками.
 * Отметить выполненным можно прямо на рабочем столе — без открытия приложения.
 */
class DayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "ru.memento.widget.REFRESH_DAY"

        private val ROW_IDS = intArrayOf(
            R.id.day_row0, R.id.day_row1, R.id.day_row2, R.id.day_row3, R.id.day_row4,
        )
        private val TIME_IDS = intArrayOf(
            R.id.day_time0, R.id.day_time1, R.id.day_time2, R.id.day_time3, R.id.day_time4,
        )
        private val TITLE_IDS = intArrayOf(
            R.id.day_title0, R.id.day_title1, R.id.day_title2, R.id.day_title3, R.id.day_title4,
        )
        private val CHECK_IDS = intArrayOf(
            R.id.day_check0, R.id.day_check1, R.id.day_check2, R.id.day_check3, R.id.day_check4,
        )

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val store = Store.get(context)
            val v = RemoteViews(context.packageName, R.layout.widget_day)
            val today = store.today().sortedWith(compareBy({ it.done }, { Ru.timeToMinutes(it.time) }))
            val left = today.count { !it.done }

            v.setTextViewText(
                R.id.day_count,
                if (today.isEmpty()) context.getString(R.string.nothing_today) else Ru.eventsWord(left),
            )

            for (i in 0..4) {
                val r = today.getOrNull(i)
                if (r == null) {
                    v.setViewVisibility(ROW_IDS[i], View.GONE)
                    continue
                }
                v.setViewVisibility(ROW_IDS[i], View.VISIBLE)
                v.setTextViewText(TIME_IDS[i], r.time)
                v.setTextViewText(
                    TITLE_IDS[i],
                    if (r.place.isNotBlank()) "${r.title} · ${r.place}" else r.title,
                )
                v.setImageViewResource(CHECK_IDS[i], if (r.done) R.drawable.bg_check_on else R.drawable.bg_check_off)
                v.setOnClickPendingIntent(ROW_IDS[i], WidgetIntents.openReminder(context, 40 + i, r.id))
                v.setOnClickPendingIntent(CHECK_IDS[i], WidgetIntents.toggleDone(context, 50 + i, r.id))
            }
            v.setOnClickPendingIntent(R.id.widget_day_root, WidgetIntents.openApp(context, 60))
            v.setOnClickPendingIntent(R.id.day_plus, WidgetIntents.newReminder(context, 61))
            manager.updateAppWidget(id, v)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            Widgets.ids(context, DayWidgetProvider::class.java).forEach { update(context, manager, it) }
        }
    }
}
