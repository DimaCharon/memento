package ru.memento

import android.content.Intent
import android.view.LayoutInflater
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import ru.memento.ui.MainActivity
import ru.memento.ui.ReminderSheetActivity
import java.io.File

/**
 * Рисует экраны приложения так, как их увидит человек, и складывает PNG в screenshots/.
 * Картинки прикладываются к сборке — по ним видно, что интерфейс ровный.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenshotsTest {

    private fun main(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        TestSupport.idle()
        TestSupport.layoutAt(a.window.decorView)
        return a
    }

    private fun shot(a: MainActivity, tab: String?, name: String) {
        if (tab != null) {
            a.show(tab, animate = false)
            a.supportFragmentManager.executePendingTransactions()
            TestSupport.idle()
        }
        TestSupport.layoutAt(a.window.decorView)
        val f = TestSupport.capture(a.window.decorView, name)
        assertTrue("скриншот $name не создан", f.exists() && f.length() > 1000)
    }

    @Test
    fun `снимки основных экранов`() {
        TestSupport.seed()
        val a = main()
        shot(a, MainActivity.TAB_HOME, "01-главная.png")
        shot(a, MainActivity.TAB_WIDGETS, "02-виджеты.png")
        shot(a, MainActivity.TAB_CALENDAR, "03-календарь.png")
        shot(a, MainActivity.TAB_ALERTS, "04-напоминания.png")
        shot(a, MainActivity.TAB_PROFILE, "05-профиль.png")
        shot(a, MainActivity.TAB_ASSISTANT, "06-ассистент.png")
    }

    @Test
    fun `снимки новых виджетов`() {
        TestSupport.seed()
        val ctx = TestSupport.ctx()
        val manager = android.appwidget.AppWidgetManager.getInstance(ctx)
        val shadow = org.robolectric.Shadows.shadowOf(manager)

        fun shot(provider: Class<out android.appwidget.AppWidgetProvider>, rv: android.widget.RemoteViews, name: String, hDp: Int) {
            shadow.createWidget(provider, 0)
            val view = rv.apply(ctx, android.widget.FrameLayout(ctx))
            assertTrue("виджет $name не развернулся", view != null)
            TestSupport.layoutAt(view!!, wDp = 320, hDp = hDp)
            val f = TestSupport.capture(view, name, wDp = 320, hDp = hDp)
            assertTrue("скриншот $name не создан", f.exists() && f.length() > 1000)
        }

        shot(
            ru.memento.widget.NextWidgetProvider::class.java,
            ru.memento.widget.NextWidgetProvider.build(ctx), "12-виджет-следующее.png", 200,
        )
        shot(
            ru.memento.widget.WeekWidgetProvider::class.java,
            ru.memento.widget.WeekWidgetProvider.build(ctx), "13-виджет-неделя.png", 200,
        )
        shot(
            ru.memento.widget.DayWidgetProvider::class.java,
            ru.memento.widget.DayWidgetProvider.build(ctx), "14-виджет-день.png", 300,
        )
        shot(
            ru.memento.widget.QuickWidgetProvider::class.java,
            ru.memento.widget.QuickWidgetProvider.build(ctx), "15-виджет-быстро.png", 90,
        )
        shot(
            ru.memento.widget.AgentWidgetProvider::class.java,
            ru.memento.widget.AgentWidgetProvider.build(ctx), "16-виджет-агент.png", 160,
        )
    }

    @Test
    fun `снимок регистрации`() {
        TestSupport.store()
        val a = Robolectric.buildActivity(ru.memento.ui.WelcomeActivity::class.java).setup().get()
        TestSupport.idle()
        TestSupport.layoutAt(a.window.decorView)
        val f = TestSupport.capture(a.window.decorView, "11-регистрация.png")
        assertTrue("скриншот регистрации не создан", f.exists() && f.length() > 1000)
    }

    @Test
    fun `снимок нового напоминания`() {
        TestSupport.seed()
        val intent = Intent(TestSupport.ctx(), ReminderSheetActivity::class.java)
            .setAction(ReminderSheetActivity.ACTION_NEW)
        val a = Robolectric.buildActivity(ReminderSheetActivity::class.java, intent).setup().get()
        TestSupport.idle()
        TestSupport.layoutAt(a.window.decorView)
        val f = TestSupport.capture(a.window.decorView, "07-новое-напоминание.png")
        assertTrue(f.exists() && f.length() > 1000)
    }

    @Test
    fun `снимок уведомления о напоминании`() {
        val v = LayoutInflater.from(TestSupport.ctx()).inflate(R.layout.notification_reminder, null, false)
        v.findViewById<TextView>(R.id.notif_when).text = "NORY · Через 23 мин"
        v.findViewById<TextView>(R.id.notif_title).text = "Встреча с командой"
        v.findViewById<TextView>(R.id.notif_body).text = "Офис, переговорная · 19:47"
        TestSupport.layoutAt(v, wDp = 380, hDp = 150)
        val f = TestSupport.capture(v, "09-уведомление.png", wDp = 380, hDp = 150)
        assertTrue("скриншот уведомления не создан", f.exists() && f.length() > 1000)
    }

    @Test
    fun `снимок уведомления ассистента`() {
        val v = LayoutInflater.from(TestSupport.ctx()).inflate(R.layout.notification_ai, null, false)
        v.findViewById<TextView>(R.id.ai_title).text = "NORY · агент"
        v.findViewById<TextView>(R.id.ai_counter).text = "шаг 2 из 4"
        v.findViewById<TextView>(R.id.ai_text).text = "Создаю напоминание «Встреча с командой»"
        TestSupport.layoutAt(v, wDp = 380, hDp = 150)
        val f = TestSupport.capture(v, "10-уведомление-агента.png", wDp = 380, hDp = 150)
        assertTrue("скриншот уведомления агента не создан", f.exists() && f.length() > 1000)
    }

    @Test
    fun `снимок виджета на рабочем столе`() {
        val v = LayoutInflater.from(TestSupport.ctx()).inflate(R.layout.widget_today_preview, null, false)
        TestSupport.layoutAt(v, wDp = 320, hDp = 180)
        val f = TestSupport.capture(v, "08-виджет.png", wDp = 320, hDp = 180)
        assertTrue(f.exists() && f.length() > 1000)
    }

    @Test
    fun `скриншоты складываются рядом с проектом`() {
        val dir = TestSupport.screenshotDir()
        assertTrue("папка скриншотов должна существовать", dir.exists() || dir.mkdirs())
        assertTrue(File(dir.absolutePath).isDirectory)
    }
}
