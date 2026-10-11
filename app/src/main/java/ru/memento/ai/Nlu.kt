package ru.memento.ai

import ru.memento.data.Priority
import ru.memento.data.Reminder
import ru.memento.data.Repeat
import ru.memento.data.Ru

/**
 * Локальный разбор фразы (порт NLU-модуля с сайта). Работает офлайн и мгновенно:
 * дата, время, повтор, приоритет, место, тип действия и уверенность.
 * Если есть интернет и ключ — то же самое дополнительно уточняет ИИ (см. Pipeline).
 */
object Nlu {

    const val MODEL = "локальный движок"

    enum class Kind { CREATE, COMPLETE, DELETE, SNOOZE, QUERY, SETTINGS, WIDGETS, NAVIGATE, UNKNOWN, CHAT }

    data class Intent(
        val kind: Kind,
        val title: String,
        var date: String? = null,
        var time: String? = null,
        var repeat: Repeat = Repeat.NONE,
        var priority: Priority = Priority.MEDIUM,
        var notes: String = "",
        var place: String = "",
        var leadMinutes: Int = 10,
        var targetRef: String? = null,
        var minutes: Int? = null,
        var query: String = "today",
        var settingKey: String? = null,
        var settingValue: Any? = null,
        var screen: String? = null,
        var confidence: Double = 0.7,
        var normalized: String = "",
        val raw: String = "",
    ) {
        val confidenceLabel: String
            get() = if (confidence >= 0.82) "высокая" else if (confidence >= 0.62) "средняя" else "низкая"
    }

    // ------------------------------- подсказки -------------------------------

    private val WEEKDAY_FORMS: List<Pair<String, Int>> = listOf(
        "воскресенье" to 0, "понедельник" to 1, "вторник" to 2, "среду" to 3, "среда" to 3,
        "четверг" to 4, "пятницу" to 5, "пятница" to 5, "субботу" to 6, "суббота" to 6,
    )
    private val MONTH_KEYS = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    private const val MIN_FORMS = "(мин|минуту|минуты|минут|минутки)"
    private const val HOUR_FORMS = "(час|часа|часов)"
    private const val DAY_FORMS = "(день|дня|дней|дн)"

    private val HI_HINTS = listOf("важно", "срочно", "критично", "обязательно", "приоритет высокий", "не забыть", "самое важное")
    private val LO_HINTS = listOf("не срочно", "низкий приоритет", "когда-нибудь", "спокойно")

    private val ICON_HINTS: List<Pair<Regex, String>> = listOf(
        Regex("трениров|спорт|фитнес|зал|бег|йог") to "dumbbell",
        Regex("позвон|звонок|созвон|перезвон") to "phone",
        Regex("встреч|митинг|собес|переговор") to "briefcase",
        Regex("книг|читать|прочитать|глава") to "book",
        Regex("куп|магазин|продукт|заказ.*доставк") to "cart",
        Regex("таблетк|лекарств|витамин|врач|анализ") to "pill",
        Regex("почт|письм|email|мейл") to "mail",
        Regex("платеж|оплат|счет|счёт|кредит|налог|подписк") to "card",
        Regex("подарок|день рожден|поздравить") to "gift",
        Regex("посылк|заказ|отправ") to "box",
        Regex("заправ|авто|машин|техосмотр") to "car",
        Regex("мама|папа|бабушк|дедушк|семь") to "home",
        Regex("отчет|отчёт|презентац|дедлайн|проект") to "briefcase",
        Regex("зарядк|тренировк|вода|сон|привычк") to "target",
        Regex("метр|поезд|рейс|билет") to "clock",
        Regex("онлайн|сайт|ссылка") to "globe",
    )

    fun pickIcon(text: String): String {
        for ((re, icon) in ICON_HINTS) if (re.containsMatchIn(text)) return icon
        return "bell"
    }

    // ------------------------------- разбор -------------------------------

    fun parse(raw: String): Intent {
        val text0 = raw.trim().replace(Regex("\\s+"), " ")
        val lower = text0.lowercase().replace('ё', 'е')
        val T = " $lower "
        fun has(s: String) = T.contains(s)
        fun rx(regex: Regex): MatchResult? = regex.find(T)

        var confidence = 0.7
        var time: String? = null
        var date: String? = null
        var repeat = Repeat.NONE
        var priority = Priority.MEDIUM
        var place = ""
        var minutes: Int? = null
        var kind = Kind.CREATE
        var targetRef: String? = null
        var notes = ""
        var query = "today"
        var screen: String? = null
        var settingKey: String? = null
        var settingValue: Any? = null

        fun setClock(d: java.time.LocalDateTime) {
            date = Ru.toISO(d.toLocalDate())
            time = "%02d:%02d".format(d.hour, d.minute)
        }

        /* ------------------------------- дата ------------------------------- */
        if (has(" сегодня ") || has(" на сегодня ")) { date = Ru.todayISO(); confidence += 0.06 }
        else if (has(" завтра ")) { date = Ru.addDaysISO(Ru.todayISO(), 1); confidence += 0.06 }
        else if (has(" послезавтра ")) { date = Ru.addDaysISO(Ru.todayISO(), 2); confidence += 0.06 }

        if (date == null) {
            rx(Regex("через (\\d{1,2}) $DAY_FORMS"))?.let {
                date = Ru.addDaysISO(Ru.todayISO(), it.groupValues[1].toLong()); confidence += 0.05
            }
        }
        if (date == null && has(" через неделю ")) { date = Ru.addDaysISO(Ru.todayISO(), 7); confidence += 0.05 }

        if (date == null) {
            val forms = WEEKDAY_FORMS.joinToString("|") { it.first }
            rx(Regex(" следующ(?:ую|ий) (неделю|$forms) "))?.let { m ->
                val form = m.groupValues[1]
                val wd = if (form == "неделю") java.time.LocalDate.now().dayOfWeek.value % 7
                else WEEKDAY_FORMS.firstOrNull { it.first == form }?.second ?: 0
                var d = java.time.LocalDate.now().plusDays(7)
                val delta = ((wd - d.dayOfWeek.value % 7) + 7) % 7
                d = d.plusDays(delta.toLong())
                date = Ru.toISO(d); confidence += 0.05
            }
        }

        if (date == null) {
            val forms = WEEKDAY_FORMS.joinToString("|") { it.first }
            rx(Regex(" (?:в|во) ($forms) "))?.let { m ->
                val target = WEEKDAY_FORMS.firstOrNull { it.first == m.groupValues[1] }?.second ?: 0
                var delta = ((target - java.time.LocalDate.now().dayOfWeek.value % 7) + 7) % 7
                if (delta == 0) delta = 7
                date = Ru.addDaysISO(Ru.todayISO(), delta.toLong()); confidence += 0.06
            }
        }

        if (date == null) {
            rx(Regex(" (\\d{1,2})[. ](${MONTH_KEYS.joinToString("|")})[а-я]* ?(\\d{4})? "))?.let { m ->
                val mi = MONTH_KEYS.indexOfFirst { x ->
                    m.groupValues[2].startsWith(x.take(3)) || x.startsWith(m.groupValues[2].take(3))
                }
                if (mi >= 0) {
                    val now = java.time.LocalDate.now()
                    val yearStr = m.groupValues[3]
                    val year = if (yearStr.isNotBlank()) yearStr.toInt() else if (now.monthValue > mi + 1) now.year + 1 else now.year
                    val day = m.groupValues[1].toInt()
                    date = "%04d-%02d-%02d".format(year, mi + 1, day); confidence += 0.08
                }
            }
        }

        /* ------------------------------- время ------------------------------- */
        val tm = rx(Regex(" (?:в|во) (\\d{1,2})[:.](\\d{2}) "))
        if (tm != null) {
            time = "%02d:%s".format(tm.groupValues[1].toInt() % 24, tm.groupValues[2]); confidence += 0.08
        } else {
            val hm = rx(Regex(" (?:в|во) (\\d{1,2})(?![:.\\d]) "))
            if (hm != null && hm.groupValues[1].toInt() <= 23) {
                time = "%02d:00".format(hm.groupValues[1].toInt()); confidence += 0.04
            } else if (has(" в полдень ")) time = "12:00"
            else if (has(" в полночь ")) time = "00:00"
            else {
                val mn = rx(Regex("через (\\d{1,3}) $MIN_FORMS"))
                val hr = rx(Regex("через (\\d{1,2}) $HOUR_FORMS"))
                if (mn != null) {
                    setClock(java.time.LocalDateTime.now().plusMinutes(mn.groupValues[1].toLong())); confidence += 0.06
                } else if (hr != null) {
                    setClock(java.time.LocalDateTime.now().plusHours(hr.groupValues[1].toLong())); confidence += 0.05
                } else {
                    rx(Regex(" (утром|днем|вечером|ночью) "))?.let { m ->
                        val h = when (m.groupValues[1]) {
                            "утром" -> 9; "ночью" -> 22; "вечером" -> 19; else -> 14
                        }
                        time = "%02d:00".format(h); confidence += 0.03
                    }
                }
            }
        }

        /* ------------------------------ повтор ------------------------------ */
        repeat = when {
            Regex("кажд(ый|ое|ую) (день|утро|вечер|ночь)|ежедневн|по утрам|по вечерам|каждодневн").containsMatchIn(T) -> Repeat.DAILY
            Regex("по будням|по рабочим дням|кроме выходных").containsMatchIn(T) -> Repeat.WEEKDAYS
            Regex("каждую неделю|еженедельн|раз в неделю|раз в месяц|каждый месяц|ежемесячн").containsMatchIn(T) ->
                if (Regex("месяц|ежемесячн").containsMatchIn(T)) Repeat.MONTHLY else Repeat.WEEKLY
            Regex("кажд(ый|ую) (понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье)").containsMatchIn(T) -> Repeat.WEEKLY
            else -> Repeat.NONE
        }

        /* ----------------------------- приоритет ----------------------------- */
        priority = when {
            HI_HINTS.any { T.contains(it) } -> { confidence += 0.03; Priority.HIGH }
            LO_HINTS.any { T.contains(it) } -> { confidence += 0.02; Priority.LOW }
            else -> Priority.MEDIUM
        }

        /* ------------------ место, заметки, «за сколько до» ------------------ */
        rx(Regex(" (?:в|на) (офис|онлайн|дом|кафе|зал|спортзал|клиник|школ|аэропорт|вокзал|переговорн)[а-я]* "))?.let {
            place = it.groupValues[1].replaceFirstChar { ch -> ch.uppercase() }
        }
        Regex("[-—:]\\s*(.{6,})").find(text0)?.let { notes = it.groupValues[1].trim() }
        rx(Regex("за (\\d{1,3}) $MIN_FORMS до"))?.let { minutes = it.groupValues[1].toInt() }
        rx(Regex("за (\\d{1,2}) $HOUR_FORMS до"))?.let { minutes = it.groupValues[1].toInt() * 60 }
        val lead = minutes

        /* ---------------------------- вид команды ---------------------------- */
        val widgetM = Regex("виджет[а-я]* (?:на |размер[а-я]* )?(2 ?[xх×] ?2|4 ?[xх×] ?2|4 ?[xх×] ?4)").find(T)
        val snoozeWords = Regex("(отложи|отложить|перенеси|перенести|напомни позже|поставь на паузу)").containsMatchIn(T)
        val doneWords = Regex("(отметь|отметить|выполнен|готово|сделано|закрой|заверши|галочк|чек)").containsMatchIn(T)
        val delWords = Regex("(удали|удалить|убери|убрать|стереть|снос)").containsMatchIn(T)
        val navM = Regex(" (перейди|открой|покажи) (?:на )?(главн|календар|виджет|профил|агент|коннектор|подключени|уведомлен|напоминани)").find(T)
        val showWords = Regex("(покажи|показать|список|какие|что у меня|что на |сколько у меня|что запланировано)").containsMatchIn(T)

        if (widgetM != null) {
            kind = Kind.WIDGETS
            val norm = widgetM.groupValues[1].replace(Regex("[\\sх×]"), "x")
            if (norm in listOf("2x2", "4x2", "4x4")) settingValue = norm
            confidence += 0.12
        } else if (Regex("^(помощь|help|что ты умеешь|команды)").containsMatchIn(T.trim())) {
            kind = Kind.CHAT
        } else if (snoozeWords && !T.contains("виджет")) {
            kind = Kind.SNOOZE
            val mm = rx(Regex("на (\\d{1,3}) $MIN_FORMS"))
            val hh = rx(Regex("на (\\d{1,2}) $HOUR_FORMS"))
            minutes = when {
                mm != null -> mm.groupValues[1].toInt()
                hh != null -> hh.groupValues[1].toInt() * 60
                else -> 10
            }
            targetRef = cleanTarget(text0.replace(Regex("^.*?(отложи|отложить|перенеси|перенести|напомни позже)"), "", RegexOption.IGNORE_CASE))
            confidence += 0.1
        } else if (delWords) {
            kind = Kind.DELETE
            targetRef = cleanTarget(text0.replace(Regex("^.*?(удали|удалить|убери|убрать|стереть)"), "", RegexOption.IGNORE_CASE))
            confidence += 0.1
        } else if (doneWords && Regex("(напомина|задач|его|ее|её|это|выполн|готово|сделано)").containsMatchIn(T)) {
            kind = Kind.COMPLETE
            targetRef = cleanTarget(
                text0.replace(Regex("^.*?(отметь|отметить|выполненным|выполнено|готово|сделано|закрой|заверши)"), "", RegexOption.IGNORE_CASE)
                    .replace(Regex("^\\s*(напоминание|задачу|задача)\\s*"), "", RegexOption.IGNORE_CASE),
            )
            confidence += 0.1
        } else if (Regex("(поставь|включи|выключи|сделай|измени|поменяй|переключи).*(звук|уведомлен|тему|темн|светл|имя|проверк|виджет)|^(уведомления|звук) (вкл|выкл)").containsMatchIn(T.trim()) && widgetM == null) {
            kind = Kind.SETTINGS
            val off = Regex("(выключ|отключ|убери)").containsMatchIn(T)
            when {
                T.contains("уведомлен") -> { settingKey = "notificationsEnabled"; settingValue = !off }
                T.contains("звук") -> { settingKey = "soundEnabled"; settingValue = !off }
                T.contains("проверк") -> { settingKey = "verification"; settingValue = !off }
                T.contains("светл") -> { settingKey = "theme"; settingValue = "graphite" }
                Regex("темн|черн|уголь").containsMatchIn(T) -> { settingKey = "theme"; settingValue = "coal" }
                Regex("(меня зовут|имя)").containsMatchIn(T) -> {
                    Regex("(?:меня зовут|имя)\\s+([А-ЯЁA-Zа-яёa-z]+)", RegexOption.IGNORE_CASE).find(text0)?.let {
                        settingKey = "userName"; settingValue = it.groupValues[1]
                    }
                }
            }
            confidence += 0.08
        } else if (navM != null && !showWords) {
            kind = Kind.NAVIGATE
            val g = navM.groupValues[2]
            screen = when {
                g.contains("виджет") -> "widgets"
                g.contains("календар") -> "calendar"
                g.contains("профил") -> "profile"
                g.contains("агент") -> "agents"
                g.contains("коннектор") || g.contains("подключен") -> "connectors"
                g.contains("уведомлен") || g.contains("напоминани") -> "reminders"
                else -> "home"
            }
            confidence += 0.1
        } else if (showWords) {
            kind = Kind.QUERY
            query = when {
                T.contains("сегодня") -> "today"
                T.contains("недел") -> "week"
                Regex("выполн|готов").containsMatchIn(T) -> "done"
                Regex("просроч|опазд").containsMatchIn(T) -> "overdue"
                Regex("ближайш|скоро").containsMatchIn(T) -> "upcoming"
                else -> "all"
            }
            confidence += 0.1
        }

        /* ------------------------------ заголовок ------------------------------ */
        var title = text0
        if (kind == Kind.CREATE) {
            var s = " $text0 "
            s = s.replace(Regex("^\\s*(напомни|напомнить)(?: мне)?[:,\\s]*", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex("(создай|добавь|поставь|заведи)\\s+(напоминание|задачу|задачу на|напоминание на)[:,\\s]*", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" (сегодня|завтра|послезавтра|на сегодня) ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" через \\d{1,3} $MIN_FORMS ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" через \\d{1,2} $HOUR_FORMS ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" через \\d{1,2} $DAY_FORMS ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" через неделю ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" (?:в|во) \\d{1,2}[:.]\\d{2} ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" (?:в|во) \\d{1,2} ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" за \\d{1,3} $MIN_FORMS до ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" за \\d{1,2} $HOUR_FORMS до ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(
                Regex(" (каждый|каждое|каждую|ежедневно|еженедельно|ежемесячно|по утрам|по вечерам|по будням|раз в неделю|раз в месяц) (день|утро|вечер|ночь|неделю|месяц|понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье)? ?", RegexOption.IGNORE_CASE), " ",
            )
            s = s.replace(Regex(" (в|во) (понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье) ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" (следующую|следующий) (неделю|понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье) ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" на \\d{1,2} (${MONTH_KEYS.joinToString("|")})[а-я]* ?(\\d{4})? ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" \\d{1,2} (${MONTH_KEYS.joinToString("|")})[а-я]* ?(\\d{4})? ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex(" это (важно|срочно) ", RegexOption.IGNORE_CASE), " ")
            s = s.replace(Regex("\\s{2,}"), " ").trim()
            s = s.trim('-', '—', ',', ':', ';', ' ')
            title = if (s.length >= 2) s.replaceFirstChar { it.uppercase() } else text0
            if (title.length > 80) title = title.take(77).trim() + "…"
        } else if (kind != Kind.CHAT && kind != Kind.SETTINGS) {
            title = if (targetRef.isNullOrBlank()) "" else targetRef
        }

        if (kind == Kind.CREATE) {
            if (date == null) date = Ru.todayISO()
            if (time == null) {
                val d = java.time.LocalDateTime.now().plusHours(1)
                time = "%02d:%02d".format(d.hour, d.minute)
                confidence -= 0.12
            }
        }

        confidence = confidence.coerceIn(0.35, 0.97)

        val normalized = buildString {
            append(if (title.isNotBlank()) title else text0)
            date?.let { append(" · ").append(it) }
            time?.let { append(" · ").append(it) }
            if (repeat != Repeat.NONE) append(" · ").append(repeat.id)
        }

        return Intent(
            kind = kind,
            title = title.ifBlank { text0 },
            date = date,
            time = time,
            repeat = repeat,
            priority = priority,
            notes = notes,
            place = place,
            leadMinutes = lead ?: 10,
            targetRef = targetRef,
            minutes = minutes,
            query = query,
            settingKey = settingKey,
            settingValue = settingValue,
            screen = screen,
            confidence = confidence,
            normalized = normalized,
            raw = raw,
        )
    }

    /** Остаток команды чистим от служебных слов, чтобы найти нужное напоминание. */
    fun cleanTarget(s: String): String = s
        .replace(Regex("^\\s*(команду|встречу|напоминание|задачу|запись)?\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex(" на \\d{1,3} (минут|минуту|минуты|час|часа|часов)\\S*", RegexOption.IGNORE_CASE), "")
        .replace(Regex(" за \\d{1,3} $MIN_FORMS", RegexOption.IGNORE_CASE), "")
        .replace(Regex(" (сегодня|завтра|послезавтра) ", RegexOption.IGNORE_CASE), " ")
        .replace(Regex(" (в|во) \\d{1,2}[:.]\\d{2} ", RegexOption.IGNORE_CASE), " ")
        .trim('-', '—', ',', ':', ';', ' ')
        .trim()

    /** Поиск напоминания по свободному описанию («тренировку», «встречу с командой»). */
    fun findReminder(ref: String?, list: List<Reminder>): Reminder? {
        if (ref.isNullOrBlank()) return null
        val r = ref.lowercase().trim()
        if (r.isEmpty()) return null
        val active = list.filter { !it.done }
        val pool = if (active.isNotEmpty()) active else list
        val words = r.replace(Regex("напоминание|задачу|задача|запись"), "")
            .split(Regex("\\s+")).filter { it.length > 2 }
        if (words.isEmpty()) return null
        var best: Reminder? = null
        var bestScore = 0.0
        for (rem in pool) {
            val hay = "${rem.title} ${rem.notes} ${rem.place}".lowercase()
            var score = 0.0
            for (w in words) {
                if (hay.contains(w)) score += 1.0
                else if (hay.contains(w.take(maxOf(4, w.length - 2)))) score += 0.6
            }
            if (score > bestScore) { bestScore = score; best = rem }
        }
        return if (bestScore >= 0.6) best else null
    }
}
