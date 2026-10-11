package ru.memento.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.memento.data.NotifKind
import ru.memento.data.Repeat
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.notify.Notifier

/** Будильник сработал: показываем карточку-уведомление и обновляем базу. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val store = Store.get(context)
        val r = store.byId(id) ?: return
        if (r.done) return

        // Doze мог разбудить раньше времени — подождём и попробуем снова
        val trigger = Ru.triggerMillis(r)
        if (System.currentTimeMillis() < trigger - 5_000) {
            AlarmScheduler.scheduleReminder(context, r)
            return
        }

        if (store.settings.notificationsEnabled) Notifier.showReminder(context, r)
        store.addNotification(r.title, Ru.notifBody(r), r.id, if (r.snoozedUntil != null) NotifKind.SNOOZE else NotifKind.DUE)

        if (r.repeat != Repeat.NONE) {
            store.advanceRepeat(r)
            store.upsert(r)
            AlarmScheduler.scheduleReminder(context, r)
        } else {
            r.firedAt = System.currentTimeMillis()
            store.upsert(r)
        }
        store.changed(reschedule = false)
    }
}
