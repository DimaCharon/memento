package ru.memento.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.memento.R
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.ui.MainActivity

/** Виджет «Агент»: последний ответ ассистента и кнопка «Спросить». */
class AgentWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "ru.memento.widget.REFRESH_AGENT"

        /** Собирает карточку виджета: используется и на телефоне, и в тестах/превью. */
        fun build(context: Context): RemoteViews {
            val store = Store.get(context)
            val last = store.logs.lastOrNull()
            val v = RemoteViews(context.packageName, R.layout.widget_agent)

            val text = when {
                store.queue.isNotEmpty() -> "Выполняю: «${store.queue.first().text}»"
                last != null -> last.text
                store.chat.lastOrNull { it.role == "agent" } != null -> store.chat.last { it.role == "agent" }.text
                else -> context.getString(R.string.widget_no_agent)
            }
            v.setTextViewText(R.id.agent_text, text)
            v.setTextViewText(
                R.id.agent_time,
                when {
                    store.queue.isNotEmpty() -> context.getString(R.string.thinking)
                    last != null -> "${last.agent} · ${Ru.relativeLabel(last.at)}"
                    else -> ""
                },
            )
            val ask = WidgetIntents.openApp(context, 70, MainActivity.TAB_ASSISTANT)
            v.setOnClickPendingIntent(R.id.agent_ask, ask)
            v.setOnClickPendingIntent(R.id.widget_agent_root, ask)
            return v
        }

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, build(context))
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            Widgets.ids(context, AgentWidgetProvider::class.java).forEach { update(context, manager, it) }
        }
    }
}
