package ru.memento

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.memento.data.NotifKind
import ru.memento.data.Reminder
import ru.memento.data.Ru
import ru.memento.data.Store

@RunWith(RobolectricTestRunner::class)
class StoreTest {

    @Test
    fun `демо-данные создают пять событий на сегодня`() {
        val s = TestSupport.seed()
        val all = s.all()
        assertEquals("всего записей должно быть 6, а есть ${all.map { "${it.date} ${it.title}" }}", 6, all.size)
        val today = s.today()
        assertTrue(
            "сегодня ${Ru.todayISO()}, а в базе ${all.map { it.date }.distinct()}; сегодня нашлось ${today.size}",
            today.size == 5,
        )
        assertTrue("одно демо-дело уже выполнено", today.any { it.done })
    }

    @Test
    fun `статистика дня считает выполненные`() {
        val s = TestSupport.seed()
        val (done, total) = s.todayStats()
        assertEquals("выполнено $done из $total (${s.today().map { "${it.time} ${it.title} done=${it.done}" }})", 1, done)
        assertEquals("всего за сегодня: $total", 5, total)
    }

    @Test
    fun `создание, изменение и удаление напоминания`() {
        val s = TestSupport.store()
        val r = Reminder(
            id = Store.uid("rem"), title = "Тест", date = Ru.todayISO(), time = "10:00",
        )
        s.upsert(r)
        assertEquals(1, s.all().size)
        assertNotNull(s.byId(r.id))

        r.title = "Тест 2"
        s.upsert(r)
        assertEquals(1, s.all().size)
        assertEquals("Тест 2", s.byId(r.id)?.title)

        s.toggleDone(r.id, true)
        assertTrue(s.byId(r.id)!!.done)

        s.delete(r.id)
        assertEquals(0, s.all().size)
    }

    @Test
    fun `отложить напоминание ставит время снуза`() {
        val s = TestSupport.seed()
        val r = s.today().first { !it.done }
        s.snooze(r.id, 15)
        val after = s.byId(r.id)!!
        assertNotNull(after.snoozedUntil)
        assertTrue(after.snoozedUntil!! > System.currentTimeMillis())
    }

    @Test
    fun `повтор каждый день переносит на завтра`() {
        val s = TestSupport.store()
        val r = Reminder(
            id = Store.uid("rem"), title = "Зарядка", date = Ru.todayISO(), time = "08:00",
            repeat = ru.memento.data.Repeat.DAILY,
        )
        s.upsert(r)
        s.advanceRepeat(r)
        assertEquals(Ru.addDaysISO(Ru.todayISO(), 1), s.byId(r.id)?.date)
    }

    @Test
    fun `данные сохраняются в файл и читаются обратно`() {
        val s = TestSupport.store()
        s.upsert(Reminder(id = "keep-1", title = "Сохранить меня", date = Ru.todayISO(), time = "11:11"))
        s.saveNow()

        Store.resetForTests()          // забываем данные в памяти — читаем только файл
        val s2 = Store.get(TestSupport.ctx())
        assertTrue("после перезагрузки напоминание должно остаться", s2.all().any { it.title == "Сохранить меня" })
    }

    @Test
    fun `центр уведомлений и счётчик непрочитанных`() {
        val s = TestSupport.store()
        s.addNotification("Встреча с командой", "Через 23 мин", "r1", NotifKind.DUE)
        s.addNotification("Проверить статус", "Пора", "r2", NotifKind.SNOOZE)
        assertEquals(2, s.unreadCount())
        s.markAllRead()
        assertEquals(0, s.unreadCount())
        s.clearNotifications()
        assertFalse(s.unreadCount() > 0)
    }

    @Test
    fun `очередь задач для работы при закрытом приложении`() {
        val s = TestSupport.store()
        val t = s.enqueue("напомни завтра в 9:00 зарядка", "Голос")
        assertEquals(1, s.queueSize())
        assertTrue(s.queue.any { it.id == t.id })
        s.dequeue(t.id)
        assertEquals(0, s.queueSize())
    }

    @Test
    fun `настройки и профили по умолчанию`() {
        val s = TestSupport.store()
        assertEquals("graphite", s.settings.theme)
        assertEquals("4x2", s.settings.widgetLayout)
        assertTrue(s.settings.notificationsEnabled)
        assertTrue(s.profiles.isNotEmpty())
        assertEquals("Александр", s.settings.userName)
    }
}
