package ru.memento.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.memento.R
import ru.memento.alarm.ActionReceiver
import ru.memento.data.Priority
import ru.memento.data.Reminder
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.data.dotDrawableRes
import ru.memento.data.iconResFor
import ru.memento.data.label
import ru.memento.ui.MainActivity

/**
 * Красивые уведомления: тёмные карточки в стиле приложения, кнопки «Отложить» и «Выполнено»,
 * а также прогресс и результат фоновых ИИ-задач.
 */
object Notifier {

    const val CH_REMINDERS = "memento_reminders"
    const val CH_AI = "memento_ai"
    const val CH_AGENT = "memento_agent"

    const val ID_AI = 7100
    private const val ID_TEST = 7200

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val store = Store.get(context)

        val reminders = NotificationChannel(
            CH_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            enableLights(true)
            enableVibration(store.settings.soundEnabled)
            if (store.settings.soundEnabled) {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            } else {
                setSound(null, null)
            }
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val ai = NotificationChannel(
            CH_AI,
            context.getString(R.string.channel_ai),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_ai_desc)
            setShowBadge(false)
        }

        val agent = NotificationChannel(
            CH_AGENT,
            context.getString(R.string.channel_agent),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_agent_desc)
            enableVibration(false)
            setSound(null, null)
        }

        nm.createNotificationChannel(reminders)
        nm.createNotificationChannel(ai)
        nm.createNotificationChannel(agent)
    }

    fun idOf(r: Reminder): Int = (r.id.hashCode() and 0x7fffffff)

    private fun contentIntent(context: Context, r: Reminder): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_REMINDER
            putExtra("id", r.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, idOf(r), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(context: Context, r: Reminder, action: String, minutes: Int = 0): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java).apply {
            this.action = action
            putExtra("id", r.id)
            if (minutes > 0) putExtra("minutes", minutes)
        }
        val code = idOf(r) * 10 + when (action) {
            ActionReceiver.ACTION_COMPLETE -> 1
            else -> 2
        }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun viewsFor(context: Context, r: Reminder, withButtons: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.notification_reminder)
        v.setTextViewText(R.id.notif_when, context.getString(R.string.notif_in, Ru.notifWhen(r)))
        v.setTextViewText(R.id.notif_title, r.title)
        v.setTextViewText(
            R.id.notif_body,
            Ru.notifBody(r) + if (r.repeat != ru.memento.data.Repeat.NONE) " · ${r.repeat.label(context)}" else "",
        )
        v.setImageViewResource(R.id.notif_icon, iconResFor(r.icon))
        v.setImageViewResource(R.id.notif_dot, priorityDot(r.priority))
        v.setViewVisibility(R.id.notif_dot, if (r.priority == Priority.MEDIUM) android.view.View.INVISIBLE else android.view.View.VISIBLE)

        if (withButtons) {
            v.setTextViewText(R.id.notif_snooze, context.getString(R.string.snooze_10))
            v.setTextViewText(R.id.notif_done, context.getString(R.string.mark_done))
            v.setOnClickPendingIntent(R.id.notif_snooze, actionIntent(context, r, ActionReceiver.ACTION_SNOOZE, 10))
            v.setOnClickPendingIntent(R.id.notif_done, actionIntent(context, r, ActionReceiver.ACTION_COMPLETE))
        } else {
            v.setViewVisibility(R.id.notif_actions, android.view.View.GONE)
        }
        v.setOnClickPendingIntent(R.id.notif_root, contentIntent(context, r))
        return v
    }

    private fun priorityDot(p: Priority): Int = p.dotDrawableRes()

    /**
     * Карточка напоминания — как в макете: «NORY · Через 23 мин», заголовок, время, кнопки.
     *
     * Важно: кнопки живут внутри самой карточки (RemoteViews), а системные addAction не добавляем —
     * иначе под чёрной карточкой появляется белая полоса с кнопками, и уведомление выглядит криво.
     * В режиме «Чёрные» включаем colorized: система красит и внешний фон в чёрный.
     */
    fun showReminder(context: Context, r: Reminder) {
        ensureChannels(context)
        val black = Store.get(context).settings.notifStyle != "system"
        val b = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_nory)
            .setColor(if (black) COLOR_BLACK else context.getColor(R.color.accentStrong))
            .setContentTitle(r.title)
            .setContentText(Ru.notifBody(r))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(viewsFor(context, r, false))
            .setCustomBigContentView(viewsFor(context, r, true))
            .setContentIntent(contentIntent(context, r))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (black) b.setColorized(true)
        runCatching {
            NotificationManagerCompat.from(context).notify(idOf(r), b.build())
        }
    }

    /** Чёрный цвет фона уведомления (colorized). */
    private val COLOR_BLACK = 0xFF050608.toInt()

    fun cancelReminder(context: Context, r: Reminder) {
        runCatching { NotificationManagerCompat.from(context).cancel(idOf(r)) }
    }

    fun cancelAll(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }

    /** Тестовая карточка из профиля — сразу видно, как выглядят уведомления. */
    fun showTest(context: Context) {
        ensureChannels(context)
        val demo = Reminder(
            id = "test-notification",
            title = "Встреча с командой",
            notes = "",
            icon = "briefcase",
            date = Ru.todayISO(),
            time = Ru.clockOf(System.currentTimeMillis() + 23 * 60_000L),
            priority = Priority.HIGH,
            place = "Офис, переговорная",
            leadMinutes = 10,
            source = "Тест",
        )
        showReminder(context, demo)
    }

    /** Прогресс фоновой ИИ-задачи. */
    fun showAiProgress(context: Context, text: String, done: Int, total: Int): Notification {
        ensureChannels(context)
        val v = RemoteViews(context.packageName, R.layout.notification_ai)
        v.setTextViewText(R.id.ai_title, context.getString(R.string.ai_task_title))
        v.setTextViewText(R.id.ai_text, context.getString(R.string.ai_task_text, text))
        v.setViewVisibility(R.id.ai_icon, android.view.View.VISIBLE)
        v.setViewVisibility(R.id.ai_done, android.view.View.GONE)
        v.setViewVisibility(R.id.ai_progress, android.view.View.VISIBLE)
        v.setTextViewText(R.id.ai_counter, if (total > 1) "$done/$total" else "")
        v.setProgressBar(R.id.ai_progress, total.coerceAtLeast(1), done, false)

        val open = PendingIntent.getActivity(
            context, ID_AI,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, CH_AI)
            .setSmallIcon(R.drawable.ic_stat_nory)
            .setColor(COLOR_BLACK)
            .setColorized(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(v)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        val n = b.build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_AI, n) }
        return n
    }

    /** Результат ИИ-задачи: галочка и ответ ассистента. */
    fun showAiResult(context: Context, ok: Boolean, title: String, text: String) {
        ensureChannels(context)
        val v = RemoteViews(context.packageName, R.layout.notification_ai)
        v.setTextViewText(R.id.ai_title, title)
        v.setTextViewText(R.id.ai_text, text)
        v.setViewVisibility(R.id.ai_icon, android.view.View.GONE)
        v.setViewVisibility(R.id.ai_done, android.view.View.VISIBLE)
        v.setViewVisibility(R.id.ai_progress, android.view.View.GONE)
        v.setTextViewText(R.id.ai_counter, "")
        v.setImageViewResource(R.id.ai_done, if (ok) R.drawable.ic_check_small else R.drawable.ic_warning)
        v.setTextColor(R.id.ai_text, context.getColor(R.color.txt))

        val open = PendingIntent.getActivity(
            context, ID_AI,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, "assistant")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_AI)
            .setSmallIcon(R.drawable.ic_stat_nory)
            .setColor(COLOR_BLACK)
            .setColorized(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(v)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_AI, n) }
    }

    fun clearAi(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_AI) }
    }
}
