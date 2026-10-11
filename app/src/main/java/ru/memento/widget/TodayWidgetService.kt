package ru.memento.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import ru.memento.R
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.data.dotRes

/** Наполнение списка виджета «Сегодня». */
class TodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
}

class Factory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<ru.memento.data.Reminder> = emptyList()

    override fun onCreate() = Unit
    override fun onDestroy() = Unit

    override fun onDataSetChanged() {
        val store = Store.get(context)
        val today = store.today()
        items = if (today.isEmpty()) store.all().filter { !it.done }.take(5)
        else today.sortedWith(compareBy({ it.done }, { Ru.timeToMinutes(it.time) }))
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val r = items.getOrNull(position) ?: return null
        val v = RemoteViews(context.packageName, R.layout.widget_today_row)
        v.setTextViewText(R.id.row_time, r.time)
        v.setTextViewText(R.id.row_title, r.title)
        v.setInt(R.id.row_dot, "setBackgroundResource", r.priority.dotRes())
        v.setViewVisibility(R.id.row_dot, android.view.View.VISIBLE)

        // RemoteViews поддерживает только разрешённые методы — меняем ресурсы, а не tint
        v.setInt(R.id.row_check, "setBackgroundResource", if (r.done) R.drawable.bg_check_on else R.drawable.bg_check_off)
        v.setImageViewResource(R.id.row_check, if (r.done) R.drawable.ic_check_ok else R.drawable.ic_check_muted)

        // Открыть напоминание
        val openIntent = Intent().apply {
            putExtra(WidgetActionReceiver.EXTRA_KIND, WidgetActionReceiver.KIND_OPEN)
            putExtra(WidgetActionReceiver.EXTRA_ID, r.id)
        }
        v.setOnClickFillInIntent(R.id.widget_row, openIntent)

        // Отметить выполненным
        val doneIntent = Intent().apply {
            putExtra(WidgetActionReceiver.EXTRA_KIND, WidgetActionReceiver.KIND_COMPLETE)
            putExtra(WidgetActionReceiver.EXTRA_ID, r.id)
        }
        v.setOnClickFillInIntent(R.id.row_check, doneIntent)
        return v
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = false
}
