package ru.memento.data

/** Приоритет напоминания — как на сайте: Высокий / Средний / Низкий. */
enum class Priority(val id: String) {
    HIGH("high"), MEDIUM("medium"), LOW("low");

    companion object {
        fun of(id: String?): Priority = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

/** Повтор. */
enum class Repeat(val id: String) {
    NONE("none"), DAILY("daily"), WEEKDAYS("weekdays"), WEEKLY("weekly"), MONTHLY("monthly");

    companion object {
        fun of(id: String?): Repeat = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** Напоминание. Единая модель и для базы, и для ИИ-агентов, и для виджетов. */
data class Reminder(
    val id: String,
    var title: String,
    var notes: String = "",
    var icon: String = "bell",
    var date: String,                 // yyyy-MM-dd
    var time: String,                 // HH:mm
    var repeat: Repeat = Repeat.NONE,
    var priority: Priority = Priority.MEDIUM,
    var place: String = "",
    var leadMinutes: Int = 10,
    var done: Boolean = false,
    var firedAt: Long? = null,
    var snoozedUntil: Long? = null,
    var source: String = "Вручную",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
) {
    fun copyAll(): Reminder = copy()
}

enum class NotifKind(val id: String) {
    DUE("due"), SNOOZE("snooze"), DONE("done"), AGENT("agent");

    companion object {
        fun of(id: String?): NotifKind = entries.firstOrNull { it.id == id } ?: DUE
    }
}

/** Запись в центре уведомлений. */
data class NotificationItem(
    val id: String,
    val reminderId: String,
    val title: String,
    val body: String,
    val at: Long,
    val kind: NotifKind = NotifKind.DUE,
    var read: Boolean = false,
)

/** Строка журнала агентов (видно на вкладке «Ассистент»). */
data class LogEntry(
    val id: String,
    val agent: String,
    val model: String,
    val status: String,   // run | ok | warn | err
    val text: String,
    val at: Long = System.currentTimeMillis(),
)

/** Сообщение диалога с ассистентом. */
data class ChatMessage(
    val id: String,
    val role: String,     // user | agent | system
    val text: String,
    val agent: String? = null,
    val at: Long = System.currentTimeMillis(),
)

/** Задача ассистента, которая может досчитаться в фоне. */
data class AiTask(
    val id: String,
    val text: String,
    val at: Long = System.currentTimeMillis(),
    val source: String = "Голос",
)

/** Профиль пользователя (несколько профилей на одном телефоне). */
data class Profile(
    val id: String,
    val name: String,
)

/** Настройки приложения. */
data class Settings(
    var userName: String = "Александр",
    var widgetLayout: String = "4x2",              // 2x2 | 4x2 | 4x4
    var activeWidgets: MutableSet<String> = linkedSetOf("today", "next", "progress"),
    var theme: String = "graphite",                // graphite | coal
    var notificationsEnabled: Boolean = true,
    var soundEnabled: Boolean = true,
    var verification: Boolean = true,
    var backgroundTasks: Boolean = true,
    var offlineSpeech: Boolean = true,
    var activeProfileId: String = "p1",
)

/** Доступ к ИИ: OpenAI-совместимый шлюз (по умолчанию api.xkiro.com/v1). */
data class AiConfig(
    var apiKey: String = "",
    var baseUrl: String = "https://api.xkiro.com/v1",
    var model: String = "qwen/qwen3.5-flash:free",
) {
    val ready: Boolean get() = apiKey.isNotBlank()
}

/** Виджет главного экрана (карточка «Сегодня», «Следующее», …). */
data class WidgetDef(val id: String, val title: String, val caption: String, val size: String)

val WIDGET_DEFS = listOf(
    WidgetDef("today", "Сегодня", "Список на день", "4x2"),
    WidgetDef("next", "Следующее", "Что дальше", "2x2"),
    WidgetDef("progress", "Прогресс дня", "Кольцо и число", "2x2"),
    WidgetDef("week", "Ближайшая неделя", "7 дней", "4x2"),
    WidgetDef("day", "Расписание на день", "Таймлайн", "4x4"),
    WidgetDef("agent", "Агент", "Последнее действие ИИ", "4x2"),
)
