package ru.memento.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.memento.data.NotifKind
import ru.memento.data.Store
import ru.memento.notify.Notifier
import ru.memento.service.AiTaskService

/** Действия из уведомлений и виджета: «Выполнено», «Отложить», догрузка очереди ИИ. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        when (intent.action) {
            ACTION_COMPLETE -> {
                val id = intent.getStringExtra("id") ?: return
                val r = store.byId(id) ?: return
                store.toggleDone(id, true)
                store.addNotification(r.title, "Выполнено", r.id, NotifKind.DONE)
                Notifier.cancelReminder(context, r)
                store.changed()
            }

            ACTION_SNOOZE -> {
                val id = intent.getStringExtra("id") ?: return
                val minutes = intent.getIntExtra("minutes", 10)
                val r = store.byId(id) ?: return
                store.snooze(id, minutes)
                Notifier.cancelReminder(context, r)
                AlarmScheduler.scheduleReminder(context, r)
                store.addNotification(r.title, "Отложено на $minutes мин", r.id, NotifKind.SNOOZE)
                store.changed(reschedule = false)
            }

            AlarmScheduler.ACTION_QUEUE_DRAIN -> {
                if (store.settings.backgroundTasks && store.queueSize() > 0) AiTaskService.drain(context)
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "ru.memento.action.COMPLETE"
        const val ACTION_SNOOZE = "ru.memento.action.SNOOZE"
    }
}
