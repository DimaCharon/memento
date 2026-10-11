package ru.memento

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.memento.ai.Nlu
import ru.memento.data.Priority
import ru.memento.data.Reminder
import ru.memento.data.Repeat
import ru.memento.data.Ru

/** Разбор фраз — то, что слышит ассистент. Проверяем даты, время, повторы и типы действий. */
class NluTest {

    private fun parse(text: String) = Nlu.parse(text)

    @Test
    fun `завтра в 9 - создать напоминание`() {
        val i = parse("напомни завтра в 9:00 встреча с командой")
        assertEquals(Nlu.Kind.CREATE, i.kind)
        assertEquals(Ru.addDaysISO(Ru.todayISO(), 1), i.date)
        assertEquals("09:00", i.time)
        assertTrue("заголовок: '${i.title}'", i.title.lowercase().contains("встреч"))
        assertTrue("служебные слова убираются", !i.title.lowercase().contains("напомни"))
    }

    @Test
    fun `пятница в 19-30 - ближайшая пятница и точное время`() {
        val i = parse("напомни в пятницу в 19:30 ужин с семьёй")
        assertEquals(Nlu.Kind.CREATE, i.kind)
        assertEquals("19:30", i.time)
        val date = Ru.fromISO(i.date!!)
        assertEquals(5, date.dayOfWeek.value)             // пятница
        assertTrue(date > java.time.LocalDate.now())      // в будущем
        assertTrue("заголовок: '${i.title}'", i.title.lowercase().contains("ужин"))
    }

    @Test
    fun `каждый день в 8 утра`() {
        val i = parse("каждый день в 8 утра зарядка")
        assertEquals(Repeat.DAILY, i.repeat)
        assertEquals("08:00", i.time)
        assertTrue(i.title.lowercase().contains("зарядка"))
    }

    @Test
    fun `через 2 часа - время от сейчас`() {
        val i = parse("напомни через 2 часа позвонить маме")
        assertEquals(Nlu.Kind.CREATE, i.kind)
        assertEquals(Ru.todayISO(), i.date)
        val now = java.time.LocalTime.now().plusHours(2)
        val parsedMinutes = Ru.timeToMinutes(i.time!!)
        val expect = now.hour * 60 + now.minute
        assertTrue("время '${i.time}' должно быть ~$expect", Math.abs(parsedMinutes - expect) <= 2)
    }

    @Test
    fun `отложить на 30 минут`() {
        val i = parse("отложи тренировку на 30 минут")
        assertEquals(Nlu.Kind.SNOOZE, i.kind)
        assertEquals(30, i.minutes)
    }

    @Test
    fun `отметить выполненным`() {
        val i = parse("отметь звонок клиенту выполненным")
        assertEquals(Nlu.Kind.COMPLETE, i.kind)
    }

    @Test
    fun `удалить напоминание`() {
        assertEquals(Nlu.Kind.DELETE, parse("удали задачу про книгу").kind)
    }

    @Test
    fun `что сегодня - запрос`() {
        val i = parse("что у меня сегодня?")
        assertEquals(Nlu.Kind.QUERY, i.kind)
        assertEquals("today", i.query)
    }

    @Test
    fun `что на неделе - запрос недели`() {
        val i = parse("что у меня на неделе")
        assertEquals(Nlu.Kind.QUERY, i.kind)
        assertEquals("week", i.query)
    }

    @Test
    fun `виджет 4х4 - размер`() {
        val i = parse("поставь виджет 4×4")
        assertEquals(Nlu.Kind.WIDGETS, i.kind)
        assertEquals("4x4", i.settingValue)
    }

    @Test
    fun `включи уведомления - настройка`() {
        val i = parse("включи уведомления")
        assertEquals(Nlu.Kind.SETTINGS, i.kind)
        assertEquals("notificationsEnabled", i.settingKey)
        assertEquals(true, i.settingValue)
    }

    @Test
    fun `выключи звук - настройка`() {
        val i = parse("выключи звук")
        assertEquals("soundEnabled", i.settingKey)
        assertEquals(false, i.settingValue)
    }

    @Test
    fun `срочно - высокий приоритет`() {
        assertEquals(Priority.HIGH, parse("срочно напомни оплатить счёт завтра в 10:00").priority)
    }

    @Test
    fun `за 30 минут до - время предупреждения`() {
        val i = parse("напомни в 12:00 обед за 30 минут до")
        assertEquals(30, i.leadMinutes)
    }

    @Test
    fun `уверенность растёт с точным временем`() {
        val vague = parse("напомни что-то")
        val exact = parse("напомни завтра в 9:00 встреча")
        assertTrue(exact.confidence > vague.confidence)
    }

    @Test
    fun `поиск напоминания по слову`() {
        val list = listOf(
            Reminder(id = "1", title = "Тренировка", date = Ru.todayISO(), time = "19:00"),
            Reminder(id = "2", title = "Позвонить клиенту", date = Ru.todayISO(), time = "20:00"),
        )
        assertEquals("1", Nlu.findReminder("тренировку", list)?.id)
        assertEquals("2", Nlu.findReminder("позвонить клиенту", list)?.id)
    }

    @Test
    fun `очистка заголовка от служебных слов`() {
        val i = parse("создай напоминание на завтра в 15:00 позвонить в банк")
        assertTrue("заголовок: '${i.title}'", i.title.lowercase().contains("банк"))
        assertTrue("без слова «напоминание»: '${i.title}'", !i.title.lowercase().contains("напоминание"))
    }

    @Test
    fun `иконка подбирается по смыслу`() {
        assertEquals("dumbbell", Nlu.pickIcon("тренировка в зале"))
        assertEquals("phone", Nlu.pickIcon("позвонить клиенту"))
        assertEquals("bell", Nlu.pickIcon("что-то непонятное"))
    }
}
