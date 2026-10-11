package ru.memento.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.memento.data.Reminder
import ru.memento.data.Ru
import ru.memento.data.Store

/** Планировщик будильников: точное срабатывание, повторы, «отложено до», догрузка очереди ИИ. */
object AlarmScheduler {

    const val ACTION_REMINDER = "ru.memento.action.REMINDER_FIRE"
    const val ACTION_QUEUE_DRAIN = "ru.memento.action.QUEUE_DRAIN"

    private fun am(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra("id", reminder.id)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return am(context).canScheduleExactAlarms()
    }

    fun scheduleReminder(context: Context, reminder: Reminder) {
        if (reminder.done) return
        val am = am(context)
        val triggerAt = maxOf(Ru.triggerMillis(reminder), System.currentTimeMillis() + 1_000)
        val pi = pendingIntent(context, reminder)
        runCatching {
            if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }.onFailure {
            runCatching { am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        runCatching { am(context).cancel(pendingIntent(context, reminder)) }
    }

    /** Пересобрать все будильники (после изменения данных, перезагрузки, смены часового пояса). */
    fun rescheduleAll(context: Context) {
        val store = Store.get(context)
        store.all().filter { !it.done }.forEach { scheduleReminder(context, it) }
    }

    /** Сторож очереди: если что-то осталось в фоне — пробуем догрузить через N минут. */
    fun scheduleQueueWatchdog(context: Context, minutes: Int = 15) {
        val intent = Intent(context, ActionReceiver::class.java).apply { action = ACTION_QUEUE_DRAIN }
        val pi = PendingIntent.getBroadcast(
            context, 99, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + minutes * 60_000L
        runCatching {
            if (canScheduleExact(context)) am(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
