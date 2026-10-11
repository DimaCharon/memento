package ru.memento

import android.app.Application
import ru.memento.data.Memento
import ru.memento.data.Store
import ru.memento.notify.Notifier
import ru.memento.service.AiTaskService

/**
 * Точка старта: перехват сбоев, каналы уведомлений, восстановление будильников и догрузка очереди.
 * Всё, кроме перехватчика, обёрнуто в защиту: даже если что-то не поддерживается телефоном,
 * приложение откроется и покажет понятное сообщение вместо «произошёл сбой».
 */
class MementoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Самое первое: учимся сохранять текст любого сбоя на телефон
        runCatching { CrashGuard.install(this) }

        // 2. Если прошлый запуск упал — не повторяем тяжёлую работу и ничего не трогаем,
        //    чтобы человек спокойно увидел сообщение об ошибке.
        val crashed = runCatching { CrashGuard.pendingReport(this) }.getOrNull() != null

        runCatching { Notifier.ensureChannels(this) }

        val store = runCatching { Store.get(this) }.getOrNull()
        if (store != null && !crashed) {
            runCatching { Memento.sync(this) }
            if (store.settings.backgroundTasks && store.queueSize() > 0) {
                runCatching { AiTaskService.drain(this) }
            }
        }
    }
}
