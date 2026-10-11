package ru.memento

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.memento.ai.Editor
import ru.memento.ai.Nlu
import ru.memento.ai.OpResult
import ru.memento.ai.Planner
import ru.memento.data.Priority
import ru.memento.data.Repeat
import ru.memento.data.Ru
import ru.memento.data.Store

/**
 * Путь «голос → текст → действие» без сети: разбор фразы, план, выполнение, проверка.
 * Именно этот путь работает, даже если приложение закрыто (служба читает очередь).
 */
@RunWith(RobolectricTestRunner::class)
class AgentTest {

    private fun run(store: Store, phrase: String): List<OpResult> {
        val intent = Nlu.parse(phrase)
        val (ops, _) = Planner.plan(intent, store.all())
        val res = Editor.execute(ops, store)
        val problems = Editor.validate(ops, res, store)
        assertTrue("проверка не должна находить проблемы: $problems", problems.isEmpty())
        return res
    }

    @Test
    fun `фраза создаёт напоминание на завтра 9-00`() {
        val s = TestSupport.store()
        run(s, "напомни завтра в 9:00 встреча с командой")
        val r = s.all().firstOrNull()
        assertNotNull("напоминание должно появиться", r)
        assertEquals(Ru.addDaysISO(Ru.todayISO(), 1), r!!.date)
        assertEquals("09:00", r.time)
        assertTrue(r.title.lowercase().contains("встреч"))
    }

    @Test
    fun `ежедневная привычка получает повтор`() {
        val s = TestSupport.store()
        run(s, "каждый день в 8 утра зарядка")
        assertEquals(Repeat.DAILY, s.all().first().repeat)
    }

    @Test
    fun `срочность поднимает приоритет`() {
        val s = TestSupport.store()
        run(s, "срочно напомни оплатить счёт завтра в 10:00")
        assertEquals(Priority.HIGH, s.all().first().priority)
    }

    @Test
    fun `отметка выполнено закрывает нужное напоминание`() {
        val s = TestSupport.seed()
        val target = s.today().first { it.title.contains("Тренировка") }
        run(s, "отметь тренировку выполненной")
        assertTrue("тренировка должна стать выполненной", s.byId(target.id)!!.done)
    }

    @Test
    fun `отложить на 30 минут меняет время срабатывания`() {
        val s = TestSupport.seed()
        val target = s.today().first { !it.done }
        run(s, "отложи ${target.title.lowercase()} на 30 минут")
        val after = s.byId(target.id)!!
        assertNotNull("после отложить должно появиться время снуза", after.snoozedUntil)
        val expect = System.currentTimeMillis() + 30 * 60_000
        assertTrue(Math.abs(after.snoozedUntil!! - expect) < 60_000)
    }

    @Test
    fun `удаление убирает напоминание из базы`() {
        val s = TestSupport.seed()
        val target = s.today().first { it.title.contains("Позвонить") }
        run(s, "удали позвонить клиенту")
        assertEquals(null, s.byId(target.id))
    }

    @Test
    fun `вопрос о планах отвечает списком без ошибок`() {
        val s = TestSupport.seed()
        val res = run(s, "что у меня сегодня?")
        assertTrue(res.isNotEmpty())
        assertTrue(res.all { it.ok })
    }

    @Test
    fun `команда про виджет меняет размер`() {
        val s = TestSupport.store()
        run(s, "поставь виджет 4×4")
        assertEquals("4x4", s.settings.widgetLayout)
    }

    @Test
    fun `команда выключить уведомления меняет настройку`() {
        val s = TestSupport.store()
        run(s, "выключи уведомления")
        assertFalse(s.settings.notificationsEnabled)
    }

    @Test
    fun `помощь возвращает подсказку`() {
        val s = TestSupport.store()
        val reply = ru.memento.ai.Pipeline.offlineReply(s, "помощь")
        assertTrue("подсказка не должна быть пустой", reply.length > 20)
    }

    @Test
    fun `все шаги пайплайна описаны`() {
        val steps = ru.memento.ai.Pipeline.steps()
        assertTrue(steps.size >= 3)
        assertTrue(steps.all { it.first.isNotBlank() && it.second.isNotBlank() })
    }

    @Test
    fun `очередь задач обрабатывается при закрытом приложении`() {
        val s = TestSupport.store()
        val task = s.enqueue("напомни завтра в 7:30 зарядка", "Голос")
        assertEquals(1, s.queueSize())
        val intent = Nlu.parse(task.text)
        val (ops, _) = Planner.plan(intent, s.all())
        Editor.execute(ops, s)
        s.dequeue(task.id)
        assertEquals(0, s.queueSize())
        assertEquals("07:30", s.all().first().time)
    }
}
