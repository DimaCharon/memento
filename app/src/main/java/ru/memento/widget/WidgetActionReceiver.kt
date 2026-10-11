package ru.memento.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.memento.data.Store
import ru.memento.ui.MainActivity

/** Тапы внутри виджетов: открыть напоминание или отметить выполненным. */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        when (intent.getStringExtra(EXTRA_KIND)) {
            KIND_COMPLETE -> {
                val store = Store.get(context)
                val r = store.byId(id) ?: return
                val target = !r.done
                store.toggleDone(id, target)
                store.addNotification(
                    r.title,
                    if (target) "Выполнено с виджета" else "Снова активно",
                    r.id,
                    ru.memento.data.NotifKind.DONE,
                )
                store.changed()
                TodayWidgetProvider.refresh(context)
                ProgressWidgetProvider.refresh(context)
            }

            else -> {
                val open = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    action = MainActivity.ACTION_OPEN_REMINDER
                    putExtra("id", id)
                }
                runCatching { context.startActivity(open) }
            }
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val KIND_OPEN = "open"
        const val KIND_COMPLETE = "complete"
    }
}
