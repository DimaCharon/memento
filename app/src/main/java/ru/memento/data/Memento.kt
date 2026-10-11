package ru.memento.data

import android.content.Context
import ru.memento.alarm.AlarmScheduler
import ru.memento.widget.ProgressWidgetProvider
import ru.memento.widget.TodayWidgetProvider

/** Точка синхронизации: после любого изменения — будильники и виджеты на рабочем столе. */
object Memento {

    fun sync(context: Context) {
        runCatching { AlarmScheduler.rescheduleAll(context) }
        runCatching { TodayWidgetProvider.refresh(context) }
        runCatching { ProgressWidgetProvider.refresh(context) }
    }

    /** Резервное обновление виджетов, когда данные поменялись «снаружи» (ИИ, уведомление). */
    fun refreshWidgets(context: Context) {
        runCatching { TodayWidgetProvider.refresh(context) }
        runCatching { ProgressWidgetProvider.refresh(context) }
    }
}
