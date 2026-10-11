package ru.memento.data

import android.content.Context
import ru.memento.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Даты и подписи — 1:1 с сайтом (русские формы, «Через 23 мин», «Понедельник, 5 октября»).
 * Всё считается на java.time (доступно с minSdk 24 благодаря desugaring).
 */
object Ru {

    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

    fun todayISO(): String = LocalDate.now().format(ISO)
    fun toISO(d: LocalDate): String = d.format(ISO)
    fun fromISO(iso: String): LocalDate = runCatching { LocalDate.parse(iso, ISO) }.getOrDefault(LocalDate.now())

    fun addDaysISO(iso: String, days: Long): String = fromISO(iso).plusDays(days).format(ISO)

    fun timeToMinutes(t: String): Int {
        val parts = t.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    fun minutesToTime(mins: Int): String {
        val m = ((mins % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** Момент срабатывания напоминания (epoch millis). */
    fun reminderMillis(r: Reminder): Long =
        fromISO(r.date).atStartOfDay().plusMinutes(timeToMinutes(r.time).toLong())
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Когда реально стрелять: либо «отложено до», либо дата/время. */
    fun triggerMillis(r: Reminder): Long = r.snoozedUntil ?: reminderMillis(r)

    fun now(): Long = System.currentTimeMillis()

    fun fmtClock(hhmm: String): String = hhmm

    fun clockOf(millis: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(HHMM)

    fun fmtLongDate(d: LocalDate): String {
        val wd = WEEK_FULL[d.dayOfWeek.value % 7]
        return "${wd.replaceFirstChar { it.uppercase() }}, ${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]}"
    }

    fun fmtLongDateNow(): String = fmtLongDate(LocalDate.now())

    fun fmtShortDate(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]}. ${d.year}"

    fun shortDateOf(iso: String): String = fmtShortDate(fromISO(iso))

    /** «Сегодня» / «Завтра» / «12 окт» */
    fun dayLabel(iso: String): String {
        val t = todayISO()
        if (iso == t) return "Сегодня"
        if (iso == addDaysISO(t, 1)) return "Завтра"
        if (iso == addDaysISO(t, -1)) return "Вчера"
        val d = fromISO(iso)
        val sameYear = d.year == LocalDate.now().year
        return if (sameYear) "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]}"
        else "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]} ${d.year}"
    }

    fun fullDateLabel(iso: String): String {
        val d = fromISO(iso)
        return "${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]} ${d.year}"
    }

    /** «Через 23 мин» / «15 мин назад» / «Прямо сейчас» */
    fun relativeLabel(target: Long, now: Long = System.currentTimeMillis()): String {
        val diff = Math.round((target - now) / 60000.0)
        if (diff == 0L) return "Прямо сейчас"
        if (diff > 0) {
            if (diff < 60) return "Через $diff мин"
            if (diff < 60 * 24) {
                val h = diff / 60
                val m = diff % 60
                return if (m > 0) "Через $h ч $m мин" else "Через $h ч"
            }
            return "Через ${Math.round(diff / 1440.0)} дн."
        }
        val a = -diff
        if (a < 60) return "$a мин назад"
        if (a < 60 * 24) return "${a / 60} ч назад"
        return "${Math.round(a / 1440.0)} дн. назад"
    }

    fun greeting(hour: Int): String = when {
        hour < 5 -> "Доброй ночи"
        hour < 12 -> "Доброе утро"
        hour < 18 -> "Добрый день"
        else -> "Добрый вечер"
    }

    fun greetingSub(hour: Int, count: Int): String {
        if (count == 0) return "На сегодня всё запланировано."
        return when {
            hour < 12 -> "Начнём день продуктивно."
            hour < 18 -> "Сегодня важный день. Впереди $count напоминаний."
            else -> "Сегодня важный день.\nВсё получится."
        }
    }

    /** Русские множественные формы: 1 напоминание / 2 напоминания / 5 напоминаний */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "$n $one"
            mod10 in 2..4 && mod100 !in 12..14 -> "$n $few"
            else -> "$n $many"
        }
    }

    fun remindersWord(n: Int) = plural(n, "напоминание", "напоминания", "напоминаний")
    fun eventsWord(n: Int) = plural(n, "событие", "события", "событий")

    val WEEK_FULL = listOf("воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота")
    val MONTHS_GEN = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    val MONTHS_NOM = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    val MONTHS_SHORT = listOf(
        "янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек",
    )

    /** Тексты для уведомлений: «Memento · Через 23 мин», «Начало в 19:41 · Офис» */
    fun notifWhen(r: Reminder): String = relativeLabel(triggerMillis(r))

    fun notifBody(r: Reminder): String {
        val start = "Начало в ${r.time}"
        return if (r.place.isNotBlank()) "$start · ${r.place}" else start
    }
}

/** Цвет точки приоритета. */
fun Priority.dotRes(): Int = when (this) {
    Priority.HIGH -> R.color.prio_high
    Priority.MEDIUM -> R.color.prio_medium
    Priority.LOW -> R.color.prio_low
}

/** Иконка приоритета/иконка напоминания по имени из парсера. */
fun iconResFor(name: String?): Int = when (name) {
    "phone" -> R.drawable.ic_phone
    "briefcase" -> R.drawable.ic_briefcase
    "book" -> R.drawable.ic_book
    "cart" -> R.drawable.ic_cart
    "pill" -> R.drawable.ic_pill
    "mail" -> R.drawable.ic_mail
    "card" -> R.drawable.ic_card
    "gift" -> R.drawable.ic_gift
    "box" -> R.drawable.ic_folder
    "car" -> R.drawable.ic_car
    "home" -> R.drawable.ic_home
    "target" -> R.drawable.ic_target
    "dumbbell" -> R.drawable.ic_dumbbell
    "globe" -> R.drawable.ic_globe
    "clock" -> R.drawable.ic_clock
    else -> R.drawable.ic_bell
}

/** Человекочитаемый текст повтора. */
fun Repeat.label(context: Context): String = when (this) {
    Repeat.NONE -> context.getString(R.string.repeat_none)
    Repeat.DAILY -> context.getString(R.string.repeat_daily)
    Repeat.WEEKDAYS -> context.getString(R.string.repeat_weekdays)
    Repeat.WEEKLY -> context.getString(R.string.repeat_weekly)
    Repeat.MONTHLY -> context.getString(R.string.repeat_monthly)
}

fun Priority.label(context: Context): String = when (this) {
    Priority.HIGH -> context.getString(R.string.prio_high)
    Priority.MEDIUM -> context.getString(R.string.prio_medium)
    Priority.LOW -> context.getString(R.string.prio_low)
}
