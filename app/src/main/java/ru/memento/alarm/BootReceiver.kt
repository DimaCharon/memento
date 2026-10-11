package ru.memento.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.memento.data.Memento
import ru.memento.data.Store
import ru.memento.service.AiTaskService

/** Перезагрузка, обновление приложения, смена времени — восстанавливаем будильники и очередь ИИ. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        Memento.sync(context)
        if (store.settings.backgroundTasks && store.queueSize() > 0) AiTaskService.drain(context)
    }
}
