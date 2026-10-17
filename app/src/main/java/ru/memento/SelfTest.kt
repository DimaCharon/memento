package ru.memento

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.memento.alarm.AlarmScheduler
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.notify.Notifier

/**
 * Самопроверка на телефоне: по пунктам показывает, что работает, а что нет.
 * Результат можно скопировать и отправить — так сбой видно без компьютера и логов.
 */
object SelfTest {

    data class Line(val name: String, val ok: Boolean, val detail: String)

    fun run(context: Context): List<Line> {
        val out = mutableListOf<Line>()

        out += Line(
            "Приложение",
            true,
            "NORY ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}",
        )

        val store = runCatching { Store.get(context) }.getOrNull()
        val reminders = store?.all()?.size ?: -1
        out += Line(
            "Хранилище",
            store != null,
            if (store == null) "не удалось открыть базу"
            else "записей: $reminders, на сегодня: ${store.today().size}, выполнено сегодня: ${store.todayStats().first}",
        )

        val writable = runCatching {
            val f = java.io.File(context.filesDir, "selftest.tmp")
            f.writeText("ok"); f.delete()
            true
        }.getOrDefault(false)
        out += Line("Запись в память", writable, if (writable) "файлы сохраняются" else "нет доступа к своей папке")

        val exact = runCatching { AlarmScheduler.canScheduleExact(context) }.getOrDefault(false)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        out += Line(
            "Точные будильники",
            exact,
            if (exact) "система разрешает точное время (следующий: ${am?.nextAlarmClock?.triggerTime?.let { Ru.clockOf(it) } ?: "нет"})"
            else "нужно разрешение «Будильники и напоминания» — включите в Профиле",
        )

        val notifOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            listOf(Notifier.CH_REMINDERS, Notifier.CH_AI, Notifier.CH_AGENT)
                .count { nm?.getNotificationChannel(it) != null }
        } else 3
        val permNotif = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        out += Line(
            "Уведомления",
            notifOn && permNotif,
            "каналов создано: $channels/3; показ разрешён: ${if (notifOn && permNotif) "да" else "нет"}",
        )

        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val speech = runCatching { android.speech.SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        out += Line(
            "Голос",
            mic && speech,
            "микрофон: ${if (mic) "есть" else "нет"}, распознавание речи в системе: ${if (speech) "есть" else "нет"}",
        )

        val ai = store?.ai
        out += Line(
            "ИИ-ключ",
            ai?.ready == true,
            if (ai?.ready == true) "${ai.model} · ${ai.baseUrl}" else "ключ не введён — работает локальный движок",
        )

        return out
    }

    fun asText(lines: List<Line>): String = buildString {
        appendLine("NORY — самопроверка")
        appendLine("время: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}")
        appendLine()
        lines.forEach { appendLine("${if (it.ok) "[+]" else "[-]"} ${it.name}: ${it.detail}") }
    }
}
