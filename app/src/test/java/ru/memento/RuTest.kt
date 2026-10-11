package ru.memento

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.memento.data.Ru

/** Даты и подписи — как на сайте. */
class RuTest {

    @Test
    fun `относительное время`() {
        val now = 1_000_000_000_000L
        assertEquals("Через 23 мин", Ru.relativeLabel(now + 23 * 60_000, now))
        assertEquals("Через 1 ч 34 мин", Ru.relativeLabel(now + 94 * 60_000, now))
        assertEquals("15 мин назад", Ru.relativeLabel(now - 15 * 60_000, now))
        assertEquals("Прямо сейчас", Ru.relativeLabel(now, now))
    }

    @Test
    fun `русские формы множественного числа`() {
        assertEquals("1 напоминание", Ru.remindersWord(1))
        assertEquals("2 напоминания", Ru.remindersWord(2))
        assertEquals("5 напоминаний", Ru.remindersWord(5))
        assertEquals("11 напоминаний", Ru.remindersWord(11))
        assertEquals("21 напоминание", Ru.remindersWord(21))
    }

    @Test
    fun `метки дней`() {
        assertEquals("Сегодня", Ru.dayLabel(Ru.todayISO()))
        assertEquals("Завтра", Ru.dayLabel(Ru.addDaysISO(Ru.todayISO(), 1)))
        assertEquals("Вчера", Ru.dayLabel(Ru.addDaysISO(Ru.todayISO(), -1)))
    }

    @Test
    fun `время туда и обратно`() {
        assertEquals("09:05", Ru.minutesToTime(545))
        assertEquals(545, Ru.timeToMinutes("09:05"))
        assertEquals("00:00", Ru.minutesToTime(1440))
        assertEquals("23:30", Ru.minutesToTime(1410))
    }

    @Test
    fun `приветствие по часам`() {
        assertEquals("Доброе утро", Ru.greeting(8))
        assertEquals("Добрый день", Ru.greeting(15))
        assertEquals("Добрый вечер", Ru.greeting(20))
        assertEquals("Доброй ночи", Ru.greeting(3))
    }

    @Test
    fun `длинная дата по-русски`() {
        val d = java.time.LocalDate.of(2026, 10, 5)
        assertEquals("Понедельник, 5 октября", Ru.fmtLongDate(d))
        assertEquals("5 октября 2026", Ru.fullDateLabel("2026-10-05"))
    }
}
