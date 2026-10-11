package ru.memento

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ровность интерфейса: «+» строго по центру, вкладки равномерно по бокам,
 * галочки и переключатели выровнены в одну линию. Это то, что просил проверить пользователь.
 */
@RunWith(RobolectricTestRunner::class)
class UiAlignmentTest {

    private fun themed(): Context = ContextThemeWrapper(TestSupport.ctx(), R.style.Theme_Memento)

    private fun inflate(layout: Int, ctx: Context = themed()): View =
        LayoutInflater.from(ctx).inflate(layout, null, false)

    /** Центр view по горизонтали. */
    private fun at(view: View): Int = (view.left + view.right) / 2
    private fun ay(view: View): Int = (view.top + view.bottom) / 2

    @Test
    fun `кнопка плюс стоит ровно по центру экрана`() {
        val root = ActivityMainRoot.build()
        TestSupport.layoutAt(root.view)
        val fab = root.fab
        val screenCenter = root.view.width / 2
        assertTrue(
            "центр «+» ($screenCenter vs ${at(fab)}) должен совпадать с центром экрана",
            Math.abs(at(fab) - screenCenter) <= 1,
        )
    }

    @Test
    fun `четыре вкладки навигации симметричны относительно плюса`() {
        val root = ActivityMainRoot.build()
        TestSupport.layoutAt(root.view)
        val center = root.view.width / 2
        val home = Math.abs(at(root.navHome) - center)
        val profile = Math.abs(at(root.navProfile) - center)
        val calendar = Math.abs(at(root.navCalendar) - center)
        val alerts = Math.abs(at(root.navAlerts) - center)
        assertTrue("Главная и Профиль должны быть на равном расстоянии: $home vs $profile", Math.abs(home - profile) <= 1)
        assertTrue("Календарь и Напоминания должны быть на равном расстоянии: $calendar vs $alerts", Math.abs(calendar - alerts) <= 1)
        assertTrue("соседние вкладки должны идти с равным шагом", Math.abs((home - calendar) - (alerts - profile)) <= 2)
    }

    @Test
    fun `ячейки навигации одинаковой ширины и иконки на одной высоте`() {
        val root = ActivityMainRoot.build()
        TestSupport.layoutAt(root.view)
        val widths = listOf(root.navHome, root.navCalendar, root.navCenter, root.navAlerts, root.navProfile).map { it.width }
        assertTrue("ширины ячеек должны совпадать: $widths", widths.max() - widths.min() <= 1)
        val centers = listOf(root.iconHome, root.iconCalendar, root.iconAlerts, root.iconProfile).map { ay(it) }
        assertTrue("иконки должны стоять на одной высоте: $centers", centers.max() - centers.min() <= 1)
    }

    @Test
    fun `плюс приподнят над панелью как на макете`() {
        val root = ActivityMainRoot.build()
        TestSupport.layoutAt(root.view)
        val fabTop = root.view.height - root.fab.bottom
        val navTop = root.view.height - root.navBar.top
        assertTrue("верх «+» должен быть рядом с верхом панели: $fabTop vs $navTop", fabTop <= navTop + TestSupport.px(4))
    }

    @Test
    fun `галочки в списке выровнены по правому краю`() {
        val checks = mutableListOf<Int>()
        listOf("Проверить статус заказа", "Встреча с командой", "Тренировка").forEach { title ->
            val row = inflate(R.layout.item_reminder_row)
            row.findViewById<TextView>(R.id.row_title).text = title
            TestSupport.layoutAt(row, wDp = 380, hDp = 72)
            checks += row.findViewById<View>(R.id.row_check).right
        }
        assertTrue("правый край галочек должен совпадать: $checks", checks.max() - checks.min() <= 1)
    }

    @Test
    fun `галочка стоит по центру строки напоминания`() {
        val row = inflate(R.layout.item_reminder_row)
        TestSupport.layoutAt(row, wDp = 380, hDp = 72)
        val check = row.findViewById<View>(R.id.row_check)
        assertTrue("галочка должна быть по центру строки", Math.abs(ay(check) - row.height / 2) <= 2)
    }

    @Test
    fun `время и заголовок в строке стоят на одной оси`() {
        val row = inflate(R.layout.item_reminder_row)
        TestSupport.layoutAt(row, wDp = 380, hDp = 72)
        val time = row.findViewById<View>(R.id.row_time)
        val title = row.findViewById<View>(R.id.row_title)
        assertEquals("левый край строки", row.paddingLeft, time.left)
        assertTrue("заголовок правее времени", title.left > time.right)
    }

    @Test
    fun `переключатели в настройках выровнены по правому краю`() {
        val edges = mutableListOf<Int>()
        repeat(3) {
            val row = inflate(R.layout.view_setting_switch)
            TestSupport.layoutAt(row, wDp = 380, hDp = 64)
            edges += row.findViewById<View>(R.id.switch_toggle).right
        }
        assertTrue("правые края переключателей должны совпадать: $edges", edges.max() - edges.min() <= 1)
    }

    @Test
    fun `строка профиля держит имя и значок на одной оси`() {
        val row = inflate(R.layout.item_profile)
        TestSupport.layoutAt(row, wDp = 380, hDp = 72)
        val avatar = row.findViewById<View>(R.id.pr_avatar)
        val name = row.findViewById<View>(R.id.pr_name)
        assertEquals("аватар по центру строки", row.height / 2, ay(avatar))
        assertEquals("имя по центру строки", row.height / 2, ay(name))
    }
}

/** Разбор activity_main один раз для всех проверок. */
private class ActivityMainRoot(val view: View) {
    val fab: View = view.findViewById(R.id.fab)
    val navBar: View = view.findViewById(R.id.nav_bar)
    val navHome: View = view.findViewById(R.id.nav_home)
    val navCalendar: View = view.findViewById(R.id.nav_calendar)
    val navCenter: View = view.findViewById(R.id.nav_center)
    val navAlerts: View = view.findViewById(R.id.nav_alerts)
    val navProfile: View = view.findViewById(R.id.nav_profile)
    val iconHome: View = view.findViewById(R.id.nav_home_icon)
    val iconCalendar: View = view.findViewById(R.id.nav_calendar_icon)
    val iconAlerts: View = view.findViewById(R.id.nav_alerts_icon)
    val iconProfile: View = view.findViewById(R.id.nav_profile_icon)

    companion object {
        fun build(): ActivityMainRoot {
            val ctx = ContextThemeWrapper(TestSupport.ctx(), R.style.Theme_Memento)
            val v = LayoutInflater.from(ctx).inflate(R.layout.activity_main, null, false)
            return ActivityMainRoot(v)
        }
    }
}
