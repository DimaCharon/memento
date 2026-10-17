package ru.memento.ai

import android.content.Context
import org.json.JSONObject
import ru.memento.data.Priority
import ru.memento.data.Repeat
import ru.memento.data.Ru
import ru.memento.data.Store

/**
 * Пайплайн из 5 агентов (как на сайте):
 * Оркестратор → NLU → Планировщик → Редактор → Валидатор, и снова Оркестратор с ответом.
 * Локальный разбор работает всегда; если есть ключ и сеть — ИИ уточняет намерение и формулирует ответ.
 */
object Pipeline {

    data class Result(val ok: Boolean, val reply: String, val usedAi: Boolean)

    fun steps(): List<Pair<String, String>> = listOf(
        "NORY Orchestrator" to "ставит задачу и проверяет результат",
        "NLU · Парсер намерения" to "разбирает фразу: смысл, дата, время, повтор",
        "Планировщик" to "считает слоты и разрешает конфликты",
        "Редактор напоминаний" to "реально меняет данные приложения",
        "Валидатор (QA)" to "перечитывает базу и подтверждает",
    )

    suspend fun run(context: Context, store: Store, text: String, onStep: ((String) -> Unit)? = null): Result {
        val localAgent = Nlu.MODEL
        val model = if (store.ai.ready) store.ai.model else localAgent

        fun log(agent: String, status: String, s: String, aiModel: String = model) {
            store.addLog(agent, aiModel, status, s)
            onStep?.invoke(s)
        }

        log("NORY Orchestrator", "run", "Понял: «${text.trim()}». Ставлю задачу парсеру.")

        // 1. NLU — локально
        var intent = Nlu.parse(text)
        log(
            "NLU · Парсер намерения", "ok",
            "Тип: ${intent.kind.name.lowercase()}, заголовок: «${intent.title}», уверенность ${(intent.confidence * 100).toInt()}% (${intent.confidenceLabel}).",
        )

        // 2. NLU — уточнение у ИИ (необязательно)
        var usedAi = false
        if (store.ai.ready) {
            log("NLU · Парсер намерения", "run", "Уточняю у модели ${store.ai.model}…")
            val refined = refineWithAi(store, text, intent)
            if (refined != null) {
                usedAi = true
                intent = refined
                log("NLU · Парсер намерения", "ok", "Модель уточнила: ${intent.normalized}")
            } else {
                log("NLU · Парсер намерения", "warn", "Модель недоступна — работаю локальным движком.")
            }
        }

        if (intent.kind == Nlu.Kind.CHAT) {
            val reply = chatReply(store, text)
            log("NORY Orchestrator", "ok", "Диалоговый ответ")
            return Result(true, reply, usedAi)
        }

        // 3. Планировщик
        val (ops, note) = Planner.plan(intent, store.all())
        if (ops.isEmpty()) {
            val reply = note ?: "Не понял команду — попробуйте: «напомни завтра в 9:00 встреча с командой»."
            log("Планировщик", "warn", reply)
            return Result(false, reply, usedAi)
        }
        log("Планировщик", "ok", "Операций: ${ops.size}${note?.let { " · $it" } ?: ""}")

        // 4. Редактор — реальные изменения
        val results = Editor.execute(ops, store)
        results.forEachIndexed { i, r ->
            log("Редактор напоминаний", if (r.ok) "ok" else "err", r.message)
        }

        // 5. Валидатор — перечитывает базу
        val problems = if (store.settings.verification) Editor.validate(ops, results, store) else emptyList()
        if (store.settings.verification) {
            if (problems.isEmpty()) log("Валидатор (QA)", "ok", "Проверено по базе: ${ops.size} ${if (ops.size == 1) "операция" else "операции"} подтверждены.")
            else log("Валидатор (QA)", "warn", "Замечания: ${problems.joinToString("; ")}")
        }

        store.changed()

        // 6. Ответ пользователю
        val facts = results.filter { it.ok }.joinToString("; ") { it.message }
        val queryAnswer = if (intent.kind == Nlu.Kind.QUERY) Editor.describe(store, intent.query) else null
        val base = when {
            problems.isNotEmpty() -> "Сделал, но валидатор заметил: ${problems.first()}"
            queryAnswer != null -> queryAnswer
            facts.isNotBlank() -> "Сделано ✓ $facts"
            else -> note ?: "Готово."
        }
        val reply = polishWithAi(store, text, base) ?: base
        store.addMessage("agent", reply, "NORY Orchestrator")
        log("NORY Orchestrator", if (problems.isEmpty()) "ok" else "warn", "Проверено по ${store.logs.count()} шагам журнала. Задача закрыта.")

        return Result(problems.isEmpty() && results.any { it.ok }, reply, usedAi)
    }

    // ------------------------------- ИИ-уточнение -------------------------------

    private fun localeBrief(): String {
        val now = java.time.LocalDateTime.now()
        return "Сегодня: ${Ru.todayISO()} (${Ru.WEEK_FULL[now.dayOfWeek.value % 7]}), сейчас ${"%02d:%02d".format(now.hour, now.minute)}."
    }

    private fun refineWithAi(store: Store, text: String, local: Nlu.Intent): Nlu.Intent? {
        val system = """
            Ты — парсер команд приложения-напоминатора. ${localeBrief()}
            Ответь ТОЛЬКО JSON без пояснений:
            {"kind":"create|complete|delete|snooze|query|settings|widgets|navigate|chat",
             "title":"краткий заголовок дела без служебных слов",
             "date":"YYYY-MM-DD или null","time":"HH:MM или null",
             "repeat":"none|daily|weekdays|weekly|monthly","priority":"high|medium|low",
             "place":"или пусто","minutes":0,"query":"today|week|all|done|overdue|upcoming"}
            Если фраза — обычный вопрос или болтовня, kind=chat.
        """.trimIndent()
        return try {
            val raw = AiClient(store.ai).chat(
                listOf(AiClient.Msg("system", system), AiClient.Msg("user", text)),
                temperature = 0.0, maxTokens = 300,
            )
            val json = extractJson(raw) ?: return null
            val kind = when (json.optString("kind").lowercase()) {
                "create" -> Nlu.Kind.CREATE
                "complete" -> Nlu.Kind.COMPLETE
                "delete" -> Nlu.Kind.DELETE
                "snooze" -> Nlu.Kind.SNOOZE
                "query" -> Nlu.Kind.QUERY
                "settings" -> Nlu.Kind.SETTINGS
                "widgets" -> Nlu.Kind.WIDGETS
                "navigate" -> Nlu.Kind.NAVIGATE
                "chat" -> Nlu.Kind.CHAT
                else -> local.kind
            }
            val date = json.optString("date").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) } ?: local.date
            val time = json.optString("time").takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) }
                ?.let { "%02d:%s".format(it.substringBefore(':').toInt() % 24, it.substringAfter(':')) } ?: local.time
            val title = json.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: local.title
            local.copy(
                kind = kind,
                title = title,
                date = date,
                time = time,
                repeat = if (json.has("repeat")) Repeat.of(json.optString("repeat")) else local.repeat,
                priority = if (json.has("priority")) Priority.of(json.optString("priority")) else local.priority,
                place = json.optString("place").takeIf { it.isNotBlank() && it != "null" } ?: local.place,
                minutes = if (json.optInt("minutes", 0) > 0) json.optInt("minutes") else local.minutes,
                query = if (json.optString("query").isNotBlank()) json.optString("query") else local.query,
                confidence = (local.confidence + 0.15).coerceAtMost(0.98),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Красивый человеческий ответ от ИИ; при неудаче — локальная формулировка. */
    private fun polishWithAi(store: Store, userText: String, base: String): String? {
        if (!store.ai.ready) return null
        return try {
            val raw = AiClient(store.ai).chat(
                listOf(
                    AiClient.Msg(
                        "system",
                        "Ты — ассистент приложения Memento. Ответь пользователю одной короткой фразой (до 200 символов), " +
                            "по-русски, дружелюбно, без выдумок. Опирайся только на факт из строки «Сделано».",
                    ),
                    AiClient.Msg("user", "Запрос: $userText\nСделано: $base"),
                ),
                temperature = 0.4, maxTokens = 160,
            )
            raw.trim().take(400).ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    /** Свободный диалог (когда фраза — не команда, а разговор). */
    private fun chatReply(store: Store, text: String): String {
        val help = "Могу: «напомни завтра в 9:00 встреча с командой», «каждый день в 8 утра зарядка», " +
            "«отложи тренировку на 30 минут», «что у меня сегодня?», «поставь виджет 4×4»."
        if (!store.ai.ready) return help
        return try {
            val today = Editor.describe(store, "today")
            AiClient(store.ai).chat(
                listOf(
                    AiClient.Msg(
                        "system",
                        "Ты — встроенный ассистент Memento (напоминания). Отвечай кратко по-русски. " +
                            "Сводка дня: $today. Если просят действие с напоминанием — подскажи подходящую фразу-команду. " +
                            "Не выдумывай данные.",
                    ),
                    AiClient.Msg("user", text),
                ),
                temperature = 0.5, maxTokens = 300,
            ).trim()
        } catch (e: Exception) {
            help
        }
    }

    /** Быстрая проверка соединения с ИИ. */
    fun ping(store: Store): Pair<Boolean, String> = try {
        val answer = AiClient(store.ai).testKey()
        true to answer
    } catch (e: Exception) {
        false to (e.message ?: "ошибка")
    }

    /** Ответ оркестратора на свободную фразу без ИИ (для тестов и офлайна). */
    fun offlineReply(store: Store, text: String): String {
        val intent = Nlu.parse(text)
        return when (intent.kind) {
            Nlu.Kind.QUERY -> Editor.describe(store, intent.query)
            else -> "Скажите: «напомни …», «отметь … выполненным», «что у меня сегодня?»"
        }
    }

    fun intentJson(intent: Nlu.Intent): JSONObject = JSONObject().apply {
        put("kind", intent.kind.name.lowercase())
        put("title", intent.title)
        put("date", intent.date ?: JSONObject.NULL)
        put("time", intent.time ?: JSONObject.NULL)
        put("repeat", intent.repeat.id)
        put("priority", intent.priority.id)
    }
}
