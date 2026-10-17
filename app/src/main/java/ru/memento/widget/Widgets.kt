package ru.memento.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import ru.memento.ui.MainActivity
import ru.memento.ui.ReminderSheetActivity

/**
 * Общие переходы для виджетов: открыть приложение, открыть напоминание,
 * отметить выполненным, создать новое. Всё через PendingIntent — работает без запуска процесса.
 */
object WidgetIntents {

    fun openApp(context: Context, code: Int, tab: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (tab != null) putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
        }
        return PendingIntent.getActivity(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun openReminder(context: Context, code: Int, id: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = MainActivity.ACTION_OPEN_REMINDER
            putExtra("id", id)
        }
        return PendingIntent.getActivity(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun newReminder(context: Context, code: Int): PendingIntent {
        val intent = Intent(context, ReminderSheetActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            action = ReminderSheetActivity.ACTION_NEW
        }
        return PendingIntent.getActivity(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun toggleDone(context: Context, code: Int, id: String): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            putExtra(WidgetActionReceiver.EXTRA_KIND, WidgetActionReceiver.KIND_COMPLETE)
            putExtra(WidgetActionReceiver.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Все виджеты приложения: обновление одним вызовом и поиск провайдера по коду. */
object Widgets {

    fun providers(): List<Class<out AppWidgetProvider>> = listOf(
        TodayWidgetProvider::class.java,
        ProgressWidgetProvider::class.java,
        NextWidgetProvider::class.java,
        WeekWidgetProvider::class.java,
        DayWidgetProvider::class.java,
        AgentWidgetProvider::class.java,
        QuickWidgetProvider::class.java,
    )

    fun providerFor(kind: String): Class<out AppWidgetProvider>? = when (kind) {
        "today" -> TodayWidgetProvider::class.java
        "progress" -> ProgressWidgetProvider::class.java
        "next" -> NextWidgetProvider::class.java
        "week" -> WeekWidgetProvider::class.java
        "day" -> DayWidgetProvider::class.java
        "agent" -> AgentWidgetProvider::class.java
        "quick" -> QuickWidgetProvider::class.java
        else -> null
    }

    fun ids(context: Context, provider: Class<out AppWidgetProvider>): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, provider))

    fun refreshAll(context: Context) {
        runCatching { TodayWidgetProvider.refresh(context) }
        runCatching { ProgressWidgetProvider.refresh(context) }
        runCatching { NextWidgetProvider.refresh(context) }
        runCatching { WeekWidgetProvider.refresh(context) }
        runCatching { DayWidgetProvider.refresh(context) }
        runCatching { AgentWidgetProvider.refresh(context) }
        runCatching { QuickWidgetProvider.refresh(context) }
    }
}
