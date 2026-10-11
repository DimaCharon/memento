package ru.memento.ai

import ru.memento.data.Priority
import ru.memento.data.Reminder
import ru.memento.data.Repeat
import ru.memento.data.Ru
import ru.memento.data.Store

/**
 * Инструменты агента (как контракт TOOLS на сайте): планировщик строит операции,
 * редактор их выполняет, валидатор перечитывает базу.
 */
sealed interface Op {
    data class Create(
        val title: String,
        val date: String,
        val time: String,
        val repeat: Repeat = Repeat.NONE,
        val priority: Priority = Priority.MEDIUM,
        val notes: String = "",
        val place: String = "",
        val leadMinutes: Int = 10,
        val source: String = "Агент",
    ) : Op

    data class Update(val id: String, val title: String?, val date: String?, val time: String?,
                      val priority: Priority?, val notes: String?, val place: String?) : Op
    data class Complete(val id: String, val done: Boolean) : Op
    data class Snooze(val id: String, val minutes: Int) : Op
    data class Delete(val id: String) : Op
    data class Navigate(val screen: String) : Op
    data class SetSetting(val key: String, val value: Any?) : Op
    data class SetWidgets(val layout: String) : Op
    data class Query(val filter: String) : Op
}

data class OpResult(val ok: Boolean, val message: String)

/** Планировщик: превращает намерение в операции, разрешая конфликты слотов. */
object Planner {

    fun plan(intent: Nlu.Intent, world: List<Reminder>): Pair<List<Op>, String?> {
        val ops = mutableListOf<Op>()
        var note: String? = null

        when (intent.kind) {
            Nlu.Kind.CREATE -> {
                val date = intent.date ?: Ru.todayISO()
                var time = intent.time ?: "12:00"
                val today = Ru.todayISO()
                var adjusted = false

                if (date == today) {
                    val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                    if (Ru.timeToMinutes(time) <= nowMin) {
                        val d = java.time.LocalDateTime.now().plusHours(1)
                        time = "%02d:%02d".format(d.hour, d.minute)
                        adjusted = true
                    }
                }
                // Конфликт: та же минута занята — сдвигаем на 30 минут
                if (world.any { !it.done && it.date == date && it.time == time }) {
                    val m = Ru.timeToMinutes(time) + 30
                    time = "%02d:%02d".format((m / 60) % 24, m % 60)
                    adjusted = true
                }
                ops += Op.Create(
                    title = intent.title,
                    date = date,
                    time = time,
                    repeat = intent.repeat,
                    priority = intent.priority,
                    notes = intent.notes,
                    place = intent.place,
                    leadMinutes = intent.leadMinutes,
                )
                if (adjusted) note = "Планировщик сдвинул время на $time — слот был занят или уже прошёл."
            }

            Nlu.Kind.COMPLETE, Nlu.Kind.DELETE, Nlu.Kind.SNOOZE -> {
                val target = Nlu.findReminder(intent.targetRef, world)
                    ?: world.filter { !it.done }.minByOrNull { Ru.triggerMillis(it) }
                if (target == null) return emptyList<Op>() to "Не нашёл подходящее напоминание — уточни название."
                when (intent.kind) {
                    Nlu.Kind.COMPLETE -> ops += Op.Complete(target.id, true)
                    Nlu.Kind.DELETE -> ops += Op.Delete(target.id)
                    else -> ops += Op.Snooze(target.id, intent.minutes ?: 10)
                }
            }

            Nlu.Kind.QUERY -> ops += Op.Query(intent.query)
            Nlu.Kind.NAVIGATE -> intent.screen?.let { ops += Op.Navigate(it) }
            Nlu.Kind.WIDGETS -> intent.settingValue?.let {
                ops += Op.SetWidgets(it.toString())
                ops += Op.Navigate("home")
            }
            Nlu.Kind.SETTINGS -> intent.settingKey?.let { ops += Op.SetSetting(it, intent.settingValue) }
            else -> {}
        }
        return ops to note
    }
}

/** Редактор: реально меняет базу. Валидатор читает её заново и подтверждает. */
object Editor {

    fun execute(ops: List<Op>, store: Store): List<OpResult> = ops.map { op ->
        try {
            when (op) {
                is Op.Create -> {
                    val r = Reminder(
                        id = Store.uid("rem"),
                        title = op.title,
                        notes = op.notes,
                        icon = Nlu.pickIcon(op.title),
                        date = op.date,
                        time = op.time,
                        repeat = op.repeat,
                        priority = op.priority,
                        place = op.place,
                        leadMinutes = op.leadMinutes,
                        source = op.source,
                    )
                    store.upsert(r)
                    OpResult(true, "Создано «${r.title}» на ${r.date} ${r.time}")
                }

                is Op.Update -> {
                    val r = store.byId(op.id) ?: return@map OpResult(false, "Напоминание не найдено")
                    op.title?.let { r.title = it }
                    op.date?.let { r.date = it }
                    op.time?.let { r.time = it }
                    op.priority?.let { r.priority = it }
                    op.notes?.let { r.notes = it }
                    op.place?.let { r.place = it }
                    store.upsert(r)
                    OpResult(true, "Обновлено «${r.title}»")
                }

                is Op.Complete -> {
                    val r = store.byId(op.id) ?: return@map OpResult(false, "Напоминание не найдено")
                    store.toggleDone(op.id, op.done)
                    OpResult(true, "«${r.title}» — ${if (op.done) "выполнено" else "снова активно"}")
                }

                is Op.Snooze -> {
                    val r = store.byId(op.id) ?: return@map OpResult(false, "Напоминание не найдено")
                    store.snooze(op.id, op.minutes)
                    OpResult(true, "«${r.title}» отложено на ${op.minutes} мин")
                }

                is Op.Delete -> {
                    val r = store.byId(op.id) ?: return@map OpResult(false, "Напоминание не найдено")
                    store.delete(op.id)
                    OpResult(true, "Удалено «${r.title}»")
                }

                is Op.SetWidgets -> {
                    store.settings.widgetLayout = op.layout
                    store.settings.activeWidgets.add("today")
                    OpResult(true, "Виджет «Сегодня»: ${op.layout}")
                }

                is Op.SetSetting -> {
                    when (op.key) {
                        "notificationsEnabled" -> store.settings.notificationsEnabled = op.value as? Boolean ?: true
                        "soundEnabled" -> store.settings.soundEnabled = op.value as? Boolean ?: true
                        "verification" -> store.settings.verification = op.value as? Boolean ?: true
                        "theme" -> store.settings.theme = op.value?.toString() ?: "graphite"
                        "userName" -> store.settings.userName = op.value?.toString() ?: store.settings.userName
                    }
                    OpResult(true, "Настройка «${op.key}» → ${op.value}")
                }

                is Op.Query -> {
                    val list = filter(store, op.filter)
                    OpResult(true, if (list.isEmpty()) "Пусто" else list.take(8).joinToString(", ") { "${it.title} (${it.time})" })
                }

                is Op.Navigate -> OpResult(true, "Экран: ${op.screen}")
            }
        } catch (e: Exception) {
            OpResult(false, e.message ?: "Ошибка операции")
        }
    }

    /** Валидатор: перечитывает базу и проверяет, что операции реально легли. */
    fun validate(ops: List<Op>, results: List<OpResult>, store: Store): List<String> {
        val problems = mutableListOf<String>()
        ops.forEachIndexed { i, op ->
            val res = results.getOrNull(i) ?: return@forEachIndexed
            if (!res.ok) {
                problems += "шаг ${i + 1}: ${res.message}"
                return@forEachIndexed
            }
            when (op) {
                is Op.Create -> if (store.all().none { it.title == op.title && it.date == op.date && it.time == op.time }) {
                    problems += "создание «${op.title}» не подтвердилось"
                }
                is Op.Complete -> if ((store.byId(op.id)?.done ?: false) != op.done) {
                    problems += "статус «${store.byId(op.id)?.title}» не совпал"
                }
                is Op.Snooze -> if ((store.byId(op.id)?.snoozedUntil ?: 0L) < System.currentTimeMillis()) {
                    problems += "отсрочка «${store.byId(op.id)?.title}» не применилась"
                }
                is Op.Delete -> if (store.byId(op.id) != null) problems += "удаление не применилось"
                else -> {}
            }
        }
        return problems
    }

    fun filter(store: Store, filter: String): List<Reminder> {
        val t = Ru.todayISO()
        val now = System.currentTimeMillis()
        return when (filter) {
            "today" -> store.all().filter { it.date == t && !it.done }.sortedBy { Ru.timeToMinutes(it.time) }
            "week" -> store.all().filter { !it.done && it.date >= t && it.date <= Ru.addDaysISO(t, 7) }
                .sortedWith(compareBy({ it.date }, { Ru.timeToMinutes(it.time) }))
            "done" -> store.all().filter { it.done }
            "overdue" -> store.all().filter { !it.done && Ru.reminderMillis(it) < now }
            "upcoming" -> store.all().filter { !it.done && Ru.triggerMillis(it) > now }.sortedBy { Ru.triggerMillis(it) }
            else -> store.all().sortedWith(compareBy({ it.date }, { Ru.timeToMinutes(it.time) }))
        }
    }

    /** Человеческий ответ на запрос — то, что увидит пользователь. */
    fun describe(store: Store, filter: String): String = when (filter) {
        "today" -> {
            val list = filter(store, "today")
            if (list.isEmpty()) "На сегодня активных напоминаний нет — день свободен. 🎉"
            else "Сегодня ещё " + Ru.remindersWord(list.size) + ": " + list.joinToString("; ") {
                "${it.time} — ${it.title}" + if (it.place.isNotBlank()) " (${it.place})" else ""
            }
        }
        "week" -> {
            val list = filter(store, "week")
            if (list.isEmpty()) "На неделю планов нет."
            else "На неделю " + Ru.remindersWord(list.size) + ": " + list.take(7).joinToString("; ") { "${Ru.dayLabel(it.date)}, ${it.time} — ${it.title}" }
        }
        "done" -> {
            val list = filter(store, "done")
            if (list.isEmpty()) "Пока ничего не выполнено."
            else "Выполнено " + Ru.remindersWord(list.size) + ": " + list.take(8).joinToString("; ") { it.title }
        }
        "overdue" -> {
            val list = filter(store, "overdue")
            if (list.isEmpty()) "Просроченных нет — так держать."
            else "Просрочено " + Ru.remindersWord(list.size) + ": " + list.take(8).joinToString("; ") { "${it.time} — ${it.title}" }
        }
        "upcoming" -> {
            val list = filter(store, "upcoming")
            if (list.isEmpty()) "Впереди пусто."
            else "Ближайшее: " + list.take(5).joinToString("; ") { "${Ru.dayLabel(it.date)}, ${it.time} — ${it.title}" }
        }
        else -> {
            val list = filter(store, "all").filter { !it.done }
            if (list.isEmpty()) "Активных напоминаний нет."
            else "Всего " + Ru.remindersWord(list.size) + ": " + list.take(8).joinToString("; ") { "${Ru.dayLabel(it.date)}, ${it.time} — ${it.title}" }
        }
    }
}
