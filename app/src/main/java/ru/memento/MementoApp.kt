package ru.memento

import android.app.Application
import ru.memento.data.Memento
import ru.memento.data.Store
import ru.memento.notify.Notifier
import ru.memento.service.AiTaskService

/** Точка старта: каналы уведомлений, восстановление будильников и догрузка фоновых задач. */
class MementoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        val store = Store.get(this)
        Memento.sync(this)
        if (store.settings.backgroundTasks && store.queueSize() > 0) {
            AiTaskService.drain(this)
        }
    }
}
