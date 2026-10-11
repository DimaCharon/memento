package ru.memento.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Единственное хранилище приложения: напоминания, уведомления, диалог с ИИ,
 * очередь фоновых задач, настройки. Всё в приватном JSON-файле на телефоне.
 */
class Store private constructor(private val app: Context) {

    private val file = File(app.filesDir, "memento.json")
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "memento-io").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    var reminders = mutableListOf<Reminder>()
        private set
    var notifications = mutableListOf<NotificationItem>()
        private set
    var chat = mutableListOf<ChatMessage>()
        private set
    var logs = mutableListOf<LogEntry>()
        private set
    var queue = mutableListOf<AiTask>()
        private set
    var profiles = mutableListOf<Profile>()
        private set
    var settings = Settings()
    var ai = AiConfig()

    var lastAgentSummary: String = ""

    // ------------------------------- жизненный цикл -------------------------------

    private fun load() {
        val json = runCatching { if (file.exists()) JSONObject(file.readText()) else null }.getOrNull()
        if (json == null) {
            seed()
            saveNow()
            return
        }
        runCatching {
            settings = Settings(
                userName = json.optJSONObject("settings")?.optString("userName").takeUnless { it.isNullOrBlank() } ?: "Александр",
                widgetLayout = json.optJSONObject("settings")?.optString("widgetLayout")?.ifBlank { "4x2" } ?: "4x2",
                activeWidgets = (json.optJSONObject("settings")?.optJSONArray("activeWidgets")?.toStringList()
                    ?: listOf("today", "next", "progress")).toMutableSet(),
                theme = json.optJSONObject("settings")?.optString("theme")?.ifBlank { "graphite" } ?: "graphite",
                notificationsEnabled = json.optJSONObject("settings")?.optBoolean("notificationsEnabled", true) ?: true,
                soundEnabled = json.optJSONObject("settings")?.optBoolean("soundEnabled", true) ?: true,
                verification = json.optJSONObject("settings")?.optBoolean("verification", true) ?: true,
                backgroundTasks = json.optJSONObject("settings")?.optBoolean("backgroundTasks", true) ?: true,
                offlineSpeech = json.optJSONObject("settings")?.optBoolean("offlineSpeech", true) ?: true,
                activeProfileId = json.optJSONObject("settings")?.optString("activeProfileId")?.ifBlank { "p1" } ?: "p1",
            )
            ai = AiConfig(
                apiKey = json.optJSONObject("ai")?.optString("apiKey") ?: "",
                baseUrl = json.optJSONObject("ai")?.optString("baseUrl")?.ifBlank { "https://api.xkiro.com/v1" }
                    ?: "https://api.xkiro.com/v1",
                model = json.optJSONObject("ai")?.optString("model")?.ifBlank { "qwen/qwen3.5-flash:free" }
                    ?: "qwen/qwen3.5-flash:free",
            )
            profiles = json.optJSONArray("profiles").mapArray { p ->
                Profile(p.optString("id"), p.optString("name"))
            }.toMutableList()
            reminders = json.optJSONArray("reminders").mapArray { reminderFromJson(it) }.toMutableList()
            notifications = json.optJSONArray("notifications").mapArray { n ->
                NotificationItem(
                    id = n.optString("id"), reminderId = n.optString("reminderId"),
                    title = n.optString("title"), body = n.optString("body"),
                    at = n.optLong("at"), kind = NotifKind.of(n.optString("kind")),
                    read = n.optBoolean("read"),
                )
            }.toMutableList()
            chat = json.optJSONArray("chat").mapArray { c ->
                ChatMessage(c.optString("id"), c.optString("role"), c.optString("text"),
                    c.optString("agent").ifBlank { null }, c.optLong("at"))
            }.toMutableList()
            logs = json.optJSONArray("logs").mapArray { l ->
                LogEntry(l.optString("id"), l.optString("agent"), l.optString("model"),
                    l.optString("status"), l.optString("text"), l.optLong("at"))
            }.toMutableList()
            queue = json.optJSONArray("queue").mapArray { q ->
                AiTask(q.optString("id"), q.optString("text"), q.optLong("at"), q.optString("source").ifBlank { "Голос" })
            }.toMutableList()
            lastAgentSummary = json.optString("lastAgentSummary", "")
        }.onFailure { seed() }
        if (profiles.isEmpty()) profiles.add(Profile("p1", settings.userName))
        if (reminders.isEmpty() && notifications.isEmpty() && logs.isEmpty()) seed()
    }

    /**
     * Демо-набор как на сайте: 4 дела на сегодня, первое уже выполнено.
     * Времена задаются относительно «сейчас», чтобы демо всегда выглядело живым.
     */
    fun seed() {
        val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        fun at(offset: Int) = Ru.minutesToTime(nowMin + offset)
        val today = Ru.todayISO()
        reminders = mutableListOf(
            Reminder(
                id = uid("rem"), title = "Проверить статус заказа", notes = "Проверить статус и оплату",
                icon = "box", date = today, time = at(-120), priority = Priority.MEDIUM,
                place = "", leadMinutes = 10, done = true, source = "Демо",
            ),
            Reminder(
                id = uid("rem"), title = "Встреча с командой", notes = "Обсудить спринт",
                icon = "briefcase", date = today, time = at(23), priority = Priority.HIGH,
                place = "Офис, переговорная", leadMinutes = 10, source = "Демо",
            ),
            Reminder(
                id = uid("rem"), title = "Позвонить клиенту", notes = "Уточнить детали заказа",
                icon = "phone", date = today, time = at(84), priority = Priority.MEDIUM,
                place = "Онлайн", leadMinutes = 10, source = "Демо",
            ),
            Reminder(
                id = uid("rem"), title = "Тренировка", notes = "", icon = "dumbbell",
                date = today, time = at(157), priority = Priority.LOW,
                place = "Фитнес-клуб", leadMinutes = 30, source = "Демо",
            ),
            Reminder(
                id = uid("rem"), title = "Прочитать книгу", notes = "Глава 4", icon = "book",
                date = Ru.addDaysISO(today, 1), time = "22:05", priority = Priority.LOW,
                place = "Дом", leadMinutes = 10, source = "Демо",
            ),
            Reminder(
                id = uid("rem"), title = "Оплатить подписки", notes = "", icon = "card",
                date = Ru.addDaysISO(today, 3), time = "10:00", priority = Priority.MEDIUM,
                place = "Онлайн", leadMinutes = 10, repeat = Repeat.MONTHLY, source = "Демо",
            ),
        )
        notifications = mutableListOf()
        chat = mutableListOf()
        logs = mutableListOf()
        queue = mutableListOf()
        profiles = mutableListOf(Profile("p1", settings.userName))
        settings.activeProfileId = "p1"
    }

    fun resetDemo() {
        seed()
        saveNow()
        emit()
    }

    // ------------------------------- сохранение -------------------------------

    fun save() {
        io.execute { saveNow() }
    }

    @Synchronized
    fun saveNow() {
        val root = JSONObject()
        root.put("version", 1)
        root.put("settings", JSONObject().apply {
            put("userName", settings.userName)
            put("widgetLayout", settings.widgetLayout)
            put("activeWidgets", JSONArray(settings.activeWidgets.toList()))
            put("theme", settings.theme)
            put("notificationsEnabled", settings.notificationsEnabled)
            put("soundEnabled", settings.soundEnabled)
            put("verification", settings.verification)
            put("backgroundTasks", settings.backgroundTasks)
            put("offlineSpeech", settings.offlineSpeech)
            put("activeProfileId", settings.activeProfileId)
        })
        root.put("ai", JSONObject().apply {
            put("apiKey", ai.apiKey)
            put("baseUrl", ai.baseUrl)
            put("model", ai.model)
        })
        root.put("profiles", JSONArray().apply {
            profiles.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        })
        root.put("reminders", JSONArray().apply { reminders.forEach { put(it.toJson()) } })
        root.put("notifications", JSONArray().apply {
            notifications.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("reminderId", it.reminderId); put("title", it.title)
                    put("body", it.body); put("at", it.at); put("kind", it.kind.id); put("read", it.read)
                })
            }
        })
        root.put("chat", JSONArray().apply {
            chat.takeLast(200).forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("role", it.role); put("text", it.text)
                    put("agent", it.agent ?: ""); put("at", it.at)
                })
            }
        })
        root.put("logs", JSONArray().apply {
            logs.takeLast(200).forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("agent", it.agent); put("model", it.model)
                    put("status", it.status); put("text", it.text); put("at", it.at)
                })
            }
        })
        root.put("queue", JSONArray().apply {
            queue.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("text", it.text); put("at", it.at); put("source", it.source)
                })
            }
        })
        root.put("lastAgentSummary", lastAgentSummary)
        runCatching {
            val tmp = File(file.parentFile, "memento.json.tmp")
            tmp.writeText(root.toString())
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    // ------------------------------- подписчики -------------------------------

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun emit() {
        main.post { listeners.toList().forEach { runCatching { it() } } }
    }

    /** Изменили данные → сохраняем, будим слушателей, обновляем будильники и виджеты. */
    fun changed(reschedule: Boolean = true) {
        save()
        if (reschedule) Memento.sync(app)
        emit()
    }

    // ------------------------------- напоминания -------------------------------

    fun all(): List<Reminder> = reminders.toList()

    fun byId(id: String?): Reminder? = reminders.firstOrNull { it.id == id }

    fun upsert(r: Reminder) {
        val idx = reminders.indexOfFirst { it.id == r.id }
        r.updatedAt = System.currentTimeMillis()
        if (idx >= 0) reminders[idx] = r else reminders.add(r)
        sortAll()
    }

    fun delete(id: String) {
        reminders.removeAll { it.id == id }
    }

    fun toggleDone(id: String, done: Boolean = true) {
        byId(id)?.let {
            it.done = done
            it.snoozedUntil = null
            if (done) it.firedAt = null
            it.updatedAt = System.currentTimeMillis()
        }
    }

    fun snooze(id: String, minutes: Int) {
        byId(id)?.let {
            it.snoozedUntil = System.currentTimeMillis() + minutes * 60_000L
            it.firedAt = null
            it.done = false
            it.updatedAt = System.currentTimeMillis()
        }
    }

    fun sortAll() {
        reminders.sortWith(compareBy({ it.date }, { Ru.timeToMinutes(it.time) }))
    }

    fun today(): List<Reminder> {
        val t = Ru.todayISO()
        return reminders.filter { it.date == t }.sortedBy { Ru.timeToMinutes(it.time) }
    }

    /** Что должно стрелять: время пришло, не выполнено, ещё не объявлено. */
    fun due(now: Long = System.currentTimeMillis()): List<Reminder> =
        reminders.filter { r ->
            !r.done && r.repeat == Repeat.NONE &&
                r.firedAt?.let { Ru.triggerMillis(r) > it } ?: (Ru.triggerMillis(r) <= now)
        }

    fun next(now: Long = System.currentTimeMillis()): Reminder? =
        reminders.filter { !it.done && Ru.triggerMillis(it) >= now - 60_000 }
            .minByOrNull { Ru.triggerMillis(it) }

    fun overdueCount(now: Long = System.currentTimeMillis()): Int =
        reminders.count { !it.done && Ru.reminderMillis(it) < now }

    fun todayStats(): Pair<Int, Int> {
        val list = today()
        return list.count { it.done } to list.size
    }

    /** Повтор: сдвигаем на следующую дату после срабатывания. */
    fun advanceRepeat(r: Reminder) {
        val step = when (r.repeat) {
            Repeat.DAILY -> 1L
            Repeat.WEEKDAYS -> 1L
            Repeat.WEEKLY -> 7L
            Repeat.MONTHLY -> 0L
            Repeat.NONE -> return
        }
        var next = if (step == 0L) {
            val d = Ru.fromISO(r.date).plusMonths(1)
            Ru.toISO(d)
        } else {
            Ru.addDaysISO(r.date, step)
        }
        if (r.repeat == Repeat.WEEKDAYS) {
            val d = Ru.fromISO(next)
            while (d.dayOfWeek.value > 5) next = Ru.addDaysISO(next, 1)
        }
        val time = Ru.timeToMinutes(r.time)
        while (Ru.fromISO(next).atStartOfDay().plusMinutes(time.toLong())
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() <= System.currentTimeMillis()
        ) {
            next = Ru.addDaysISO(next, if (step == 0L) 30L else step)
        }
        r.date = next
        r.firedAt = null
        r.snoozedUntil = null
        r.updatedAt = System.currentTimeMillis()
    }

    // ------------------------------- уведомления -------------------------------

    fun addNotification(title: String, body: String, reminderId: String = "", kind: NotifKind = NotifKind.DUE) {
        notifications.add(
            0,
            NotificationItem(uid("ntf"), reminderId, title, body, System.currentTimeMillis(), kind),
        )
        if (notifications.size > 60) notifications = notifications.take(60).toMutableList()
    }

    fun markAllRead() {
        notifications.forEach { it.read = true }
    }

    fun clearNotifications() {
        notifications.clear()
    }

    fun unreadCount(): Int = notifications.count { !it.read }

    // ------------------------------- диалог и журнал -------------------------------

    fun addMessage(role: String, text: String, agent: String? = null): ChatMessage {
        val m = ChatMessage(uid("msg"), role, text, agent)
        chat.add(m)
        if (chat.size > 200) chat = chat.takeLast(200).toMutableList()
        return m
    }

    fun addLog(agent: String, model: String, status: String, text: String) {
        logs.add(LogEntry(uid("log"), agent, model, status, text))
        if (logs.size > 200) logs = logs.takeLast(200).toMutableList()
    }

    // ------------------------------- очередь ИИ -------------------------------

    fun enqueue(task: String, source: String = "Голос"): AiTask {
        val t = AiTask(uid("task"), task, System.currentTimeMillis(), source)
        queue.add(t)
        return t
    }

    fun dequeue(id: String) {
        queue.removeAll { it.id == id }
    }

    fun queueSize(): Int = queue.size

    companion object {
        @Volatile
        private var inst: Store? = null

        fun get(context: Context): Store {
            val c = context.applicationContext
            return inst ?: synchronized(this) {
                inst ?: Store(c).also { it.load(); inst = it }
            }
        }

        /** Только для тестов. */
        fun resetForTests() {
            synchronized(this) { inst = null }
        }

        fun uid(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"
    }
}

// ------------------------------- JSON-хелперы -------------------------------

private fun Reminder.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("title", title); put("notes", notes); put("icon", icon)
    put("date", date); put("time", time); put("repeat", repeat.id); put("priority", priority.id)
    put("place", place); put("leadMinutes", leadMinutes); put("done", done)
    put("firedAt", firedAt ?: JSONObject.NULL); put("snoozedUntil", snoozedUntil ?: JSONObject.NULL)
    put("source", source); put("createdAt", createdAt); put("updatedAt", updatedAt)
}

private fun reminderFromJson(o: JSONObject): Reminder = Reminder(
    id = o.optString("id"),
    title = o.optString("title"),
    notes = o.optString("notes"),
    icon = o.optString("icon").ifBlank { "bell" },
    date = o.optString("date").ifBlank { Ru.todayISO() },
    time = o.optString("time").ifBlank { "12:00" },
    repeat = Repeat.of(o.optString("repeat")),
    priority = Priority.of(o.optString("priority")),
    place = o.optString("place"),
    leadMinutes = o.optInt("leadMinutes", 10),
    done = o.optBoolean("done"),
    firedAt = if (o.isNull("firedAt")) null else o.optLong("firedAt"),
    snoozedUntil = if (o.isNull("snoozedUntil")) null else o.optLong("snoozedUntil"),
    source = o.optString("source").ifBlank { "Вручную" },
    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) out.add(optString(i))
    return out
}

private fun <T> JSONArray?.mapArray(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(f(it)) }
    return out
}
