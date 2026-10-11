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
    fun `снимок виджета на рабочем столе`() {
        val v = LayoutInflater.from(TestSupport.ctx()).inflate(R.layout.widget_today, null, false)
        v.findViewById<TextView>(R.id.widget_count).text = "5 напоминаний"
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
