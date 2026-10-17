package ru.memento.data

import android.content.Context
import ru.memento.alarm.AlarmScheduler
import ru.memento.widget.Widgets

/** Точка синхронизации: после любого изменения — будильники и виджеты на рабочем столе. */
object Memento {

    fun sync(context: Context) {
        runCatching { AlarmScheduler.rescheduleAll(context) }
        Widgets.refreshAll(context)
    }

    /** Резервное обновление виджетов, когда данные поменялись «снаружи» (ИИ, уведомление). */
    fun refreshWidgets(context: Context) {
        Widgets.refreshAll(context)
    }
}
