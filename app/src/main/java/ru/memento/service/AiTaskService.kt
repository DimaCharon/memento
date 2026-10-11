package ru.memento.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.memento.R
import ru.memento.ai.Pipeline
import ru.memento.alarm.AlarmScheduler
import ru.memento.data.Store
import ru.memento.notify.Notifier

/**
 * Фоновый исполнитель ИИ-задач.
 *
 * Ключевое: это foreground-служба, поэтому задача досчитывается даже если пользователь
 * закрыл приложение. Очередь лежит в файле, при перезапуске процесса (START_STICKY, будильник-сторож,
 * BOOT_COMPLETED) она догружается.
 */
class AiTaskService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = Store.get(this)

        when (intent?.action) {
            ACTION_RUN -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                val source = intent.getStringExtra(EXTRA_SOURCE) ?: "Голос"
                if (text.isNotBlank()) store.enqueue(text, source)
                store.saveNow()
            }
            else -> Unit // START_STICKY или догрузка: работаем с тем, что уже в очереди
        }

        startForegroundSafe(store.queue.firstOrNull()?.text ?: "")

        if (job?.isActive != true) {
            job = scope.launch { processQueue() }
        } else if (store.queue.isNotEmpty()) {
            // Уже работаем — просто обновим прогресс
            Notifier.showAiProgress(this, store.queue.first().text, 1, store.queue.size)
        }
        return START_STICKY
    }

    private fun startForegroundSafe(firstTask: String) {
        val notification = Notifier.showAiProgress(this, firstTask, 0, 1)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, Notifier.ID_AI, notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(Notifier.ID_AI, notification)
            }
        }.onFailure {
            // Android 12+: старт из фона может быть запрещён — тогда просто остаёмся в очереди,
            // догрузим позже будильником-сторожем или при следующем запуске.
            runCatching { stopSelf() }
        }
    }

    private suspend fun processQueue() {
        val store = Store.get(this)
        acquireWakeLock()
        try {
            var guard = 0
            while (guard++ < 20) {
                val task = store.queue.firstOrNull() ?: break
                Notifier.showAiProgress(this, task.text, 1, store.queue.size)
                val started = System.currentTimeMillis()

                val result = runCatching { Pipeline.run(this, store, task.text) }
                val ok: Boolean
                val reply: String
                if (result.isSuccess) {
                    ok = result.getOrThrow().ok
                    reply = result.getOrThrow().reply
                } else {
                    ok = false
                    reply = "Не удалось выполнить: ${result.exceptionOrNull()?.message ?: "неизвестная ошибка"}"
                    store.addLog("Memento Orchestrator", "—", "err", reply)
                    store.addMessage("agent", reply, "Memento Orchestrator")
                }

                val seconds = (System.currentTimeMillis() - started) / 1000
                store.dequeue(task.id)
                store.saveNow()

                Notifier.showAiResult(
                    this,
                    ok,
                    if (ok) getString(R.string.ai_task_done_title) else "Ассистент: нужно уточнение",
                    "$reply${if (seconds > 3) "\n· ${seconds} с" else ""}",
                )
                store.addLog(
                    "Memento Orchestrator", if (store.ai.ready) store.ai.model else "локальный движок",
                    if (ok) "ok" else "warn", "Задача из очереди закрыта за ${seconds}с",
                )
                store.changed()
            }
        } finally {
            releaseWakeLock()
            if (Store.get(this).queue.isEmpty()) {
                stopForegroundCompat()
                stopSelf()
            } else {
                AlarmScheduler.scheduleQueueWatchdog(this)
                stopForegroundCompat()
                stopSelf()
            }
            scope.cancel()
        }
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "memento:ai-task").apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Пользователь смахнул приложение из недавних: если в очереди что-то осталось — вернёмся будильником
        val store = Store.get(this)
        if (store.queue.isNotEmpty() && store.settings.backgroundTasks) {
            AlarmScheduler.scheduleQueueWatchdog(this, 1)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RUN = "ru.memento.action.AI_RUN"
        const val EXTRA_TEXT = "text"
        const val EXTRA_SOURCE = "source"

        /** Поставить фразе задачу: служба поднимется и досчитает её, даже если приложение закрыть. */
        fun enqueue(context: Context, text: String, source: String = "Голос") {
            val intent = Intent(context, AiTaskService::class.java).apply {
                action = ACTION_RUN
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_SOURCE, source)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        /** Догрузить очередь (после перезагрузки, из будильника-сторожа, из уведомления). */
        fun drain(context: Context) {
            val intent = Intent(context, AiTaskService::class.java).apply {
                action = AlarmScheduler.ACTION_QUEUE_DRAIN
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
