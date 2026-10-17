package ru.memento.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.memento.R
import ru.memento.data.Ru
import ru.memento.data.Store
import java.time.LocalDate

/** Виджет «Ближайшая неделя»: семь дней, число дел в каждом, сегодняшний день выделен. */
class WeekWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "ru.memento.widget.REFRESH_WEEK"

        private val DAY_IDS = intArrayOf(
            R.id.week_d0, R.id.week_d1, R.id.week_d2, R.id.week_d3,
            R.id.week_d4, R.id.week_d5, R.id.week_d6,
        )
        private val NUM_IDS = intArrayOf(
            R.id.week_n0, R.id.week_n1, R.id.week_n2, R.id.week_n3,
            R.id.week_n4, R.id.week_n5, R.id.week_n6,
        )
        private val COUNT_IDS = intArrayOf(
            R.id.week_c0, R.id.week_c1, R.id.week_c2, R.id.week_c3,
            R.id.week_c4, R.id.week_c5, R.id.week_c6,
        )

        /** Собирает карточку виджета: используется и на телефоне, и в тестах/превью. */
        fun build(context: Context): RemoteViews {
            val store = Store.get(context)
            val v = RemoteViews(context.packageName, R.layout.widget_week)
            val today = LocalDate.now()
            val months = context.resources.getStringArray(R.array.week_short)

            var active = 0
            for (i in 0..6) {
                val day = today.plusDays(i.toLong())
                val iso = Ru.toISO(day)
                val count = store.all().count { it.date == iso && !it.done }
                active += count
                v.setTextViewText(DAY_IDS[i], months[day.dayOfWeek.value - 1])
                v.setTextViewText(NUM_IDS[i], day.dayOfMonth.toString())
                v.setTextViewText(COUNT_IDS[i], if (count > 0) count.toString() else "·")
                val isToday = i == 0
                v.setTextColor(NUM_IDS[i], context.getColor(if (isToday) R.color.ok else R.color.txt))
                v.setTextColor(COUNT_IDS[i], context.getColor(if (count > 0) R.color.txt2 else R.color.txt3))
            }
            v.setTextViewText(R.id.week_total, Ru.remindersWord(active))
            v.setOnClickPendingIntent(R.id.widget_week_root, WidgetIntents.openApp(context, 32))
            return v
        }

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, build(context))
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            Widgets.ids(context, WeekWidgetProvider::class.java).forEach { update(context, manager, it) }
        }
    }
}
