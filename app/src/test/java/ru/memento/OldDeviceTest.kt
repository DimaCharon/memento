package ru.memento

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.FrameLayout
import android.widget.RemoteViews
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.memento.alarm.AlarmScheduler
import ru.memento.data.Reminder
import ru.memento.data.Ru
import ru.memento.notify.Notifier
import ru.memento.ui.MainActivity
import ru.memento.ui.ReminderSheetActivity
import ru.memento.widget.ProgressWidgetProvider
import ru.memento.widget.TodayWidgetProvider

/**
 * Прогон на старых телефонах: Android 8.0 (API 26) и 9.0 (API 28) — как Samsung A8 2018.
 * Ловит вызовы API, которых на этих версиях ещё нет, и разметку, недопустимую в RemoteViews.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28])
class OldDeviceTest {

    private fun launchAllScreens() {
        TestSupport.seed()
        runCatching { CrashGuard.markHealthy(TestSupport.ctx()) }
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        TestSupport.idle()
        assertTrue("главный экран должен быть создан", !a.isFinishing)
        listOf(
            MainActivity.TAB_HOME, MainActivity.TAB_WIDGETS, MainActivity.TAB_CALENDAR,
            MainActivity.TAB_ALERTS, MainActivity.TAB_PROFILE, MainActivity.TAB_ASSISTANT,
        ).forEach { tab ->
            a.show(tab, animate = false)
            a.supportFragmentManager.executePendingTransactions()
            TestSupport.idle()
            TestSupport.layoutAt(a.window.decorView)
            assertTrue("вкладка $tab не открылась", a.supportFragmentManager.findFragmentByTag("screen-$tab") != null)
        }
    }

    @Test
    fun `приложение открывается на старых версиях Android`() {
        launchAllScreens()
    }

    @Test
    fun `экран нового напоминания открывается на старых версиях`() {
        TestSupport.seed()
        runCatching { CrashGuard.markHealthy(TestSupport.ctx()) }
        val intent = Intent(TestSupport.ctx(), ReminderSheetActivity::class.java)
            .setAction(ReminderSheetActivity.ACTION_NEW)
        val a = Robolectric.buildActivity(ReminderSheetActivity::class.java, intent).setup().get()
        TestSupport.idle()
        TestSupport.layoutAt(a.window.decorView)
        assertTrue(!a.isFinishing)
    }

    /** RemoteViews разрешает только «безопасные» элементы и методы — проверяем каждую разметку. */
    @Test
    fun `виджеты и уведомления собираются на старых версиях`() {
        val ctx = TestSupport.ctx()
        val host = FrameLayout(ctx)
        listOf(
            R.layout.widget_today, R.layout.widget_today_initial, R.layout.widget_today_row,
            R.layout.widget_progress, R.layout.notification_reminder, R.layout.notification_ai,
        ).forEach { layout ->
            val rv = RemoteViews(ctx.packageName, layout)
            val view = rv.apply(ctx, host)
            assertNotNull("разметка $layout не развернулась на API ${android.os.Build.VERSION.SDK_INT}", view)
            assertTrue(view.width >= 0)
        }
    }

    @Test
    fun `виджеты наполняются данными и обновляются`() {
        TestSupport.seed()
        val ctx = TestSupport.ctx()
        val manager = AppWidgetManager.getInstance(ctx)
        // В тестовой среде виджет нужно сначала «поставить» на рабочий стол
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        val id = shadow.createWidget(TodayWidgetProvider::class.java, R.layout.widget_today)
        val id2 = shadow.createWidget(ProgressWidgetProvider::class.java, R.layout.widget_progress)
        TodayWidgetProvider.update(ctx, manager, id)
        ProgressWidgetProvider.update(ctx, manager, id2)
        TodayWidgetProvider.refresh(ctx)
        ProgressWidgetProvider.refresh(ctx)
        assertTrue("виджет должен обновиться без ошибок", manager.getAppWidgetIds(
            android.content.ComponentName(ctx, TodayWidgetProvider::class.java)).isNotEmpty())

        // Список виджета: строки строятся нашими данными
        val factory = ru.memento.widget.Factory(ctx, id)
        factory.onDataSetChanged()
        assertTrue("в виджете должны быть дела", factory.count > 0)
        val row = factory.getViewAt(0)
        assertNotNull("строка виджета должна собираться", row)
        row!!.apply(ctx, FrameLayout(ctx))
        assertTrue(factory.getCount() > 0)
    }

    @Test
    fun `уведомления показываются и разворачиваются`() {
        TestSupport.seed()
        val ctx = TestSupport.ctx()
        val store = TestSupport.seed()
        Notifier.ensureChannels(ctx)
        val r = store.today().first()
        Notifier.showReminder(ctx, r)
        Notifier.showAiProgress(ctx, "напомни завтра в 9:00 зарядка", 1, 3)
        Notifier.showAiResult(ctx, true, "Готово", "Напоминание создано")
        Notifier.showTest(ctx)
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        val shown = if (android.os.Build.VERSION.SDK_INT >= 23) nm.activeNotifications.size else 0
        assertTrue("уведомления должны быть показаны (сейчас $shown)", shown >= 1)
    }

    @Test
    fun `будильники и очередь задач работают на старых версиях`() {
        val store = TestSupport.seed()
        val ctx = TestSupport.ctx()
        AlarmScheduler.rescheduleAll(ctx)
        store.settings.soundEnabled = false
        AlarmScheduler.scheduleQueueWatchdog(ctx, 5)
        assertTrue(AlarmScheduler.canScheduleExact(ctx))

        // Очередь: задача сохраняется и обрабатывается разбором фразы
        val t = store.enqueue("напомни завтра в 7:30 зарядка", "Голос")
        assertTrue(store.queueSize() > 0)
        val intent = ru.memento.ai.Nlu.parse(t.text)
        val (ops, _) = ru.memento.ai.Planner.plan(intent, store.all())
        ru.memento.ai.Editor.execute(ops, store)
        store.dequeue(t.id)
        assertTrue("напоминание из очереди должно появиться", store.all().any { it.time == "07:30" })
    }

    @Test
    fun `каналы уведомлений создаются на Android 8 и новее`() {
        val ctx = TestSupport.ctx()
        Notifier.ensureChannels(ctx)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            listOf(Notifier.CH_REMINDERS, Notifier.CH_AI, Notifier.CH_AGENT).forEach { id ->
                assertNotNull("канал $id должен быть создан", nm.getNotificationChannel(id))
            }
        }
    }

    @Test
    fun `голосовой ввод создаётся без падения`() {
        val input = ru.memento.voice.VoiceInput(TestSupport.ctx())
        println("распознавание речи доступно: ${input.available}")
        input.release()
    }

    @Test
    fun `самопроверка работает на старых версиях`() {
        TestSupport.seed()
        val lines = SelfTest.run(TestSupport.ctx())
        assertTrue("пунктов должно быть не меньше пяти", lines.size >= 5)
        assertTrue("текст отчёта формируется", SelfTest.asText(lines).contains("Memento"))
    }

    @Test
    fun `сохранение ошибки и экран сбоя готовы`() {
        val ctx = TestSupport.ctx()
        CrashGuard.markHealthy(ctx)
        assertTrue(CrashGuard.pendingReport(ctx) == null)
        val report = "тест: проверка экрана сбоя"
        val a = Robolectric.buildActivity(
            ru.memento.ui.CrashActivity::class.java,
            ru.memento.ui.CrashActivity.intent(ctx, report),
        ).setup().get()
        assertNotNull(a)
        assertTrue(!a.isFinishing)
    }

    @Test
    fun `картинки виджетов рисуются на старых версиях`() {
        val ctx = TestSupport.ctx()
        listOf(R.drawable.bg_check_off, R.drawable.bg_check_on, R.drawable.dot_high, R.drawable.dot_medium).forEach { res ->
            val d = androidx.core.content.ContextCompat.getDrawable(ctx, res)
            assertNotNull("нет drawable $res", d)
            val size = TestSupport.px(24)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d!!.setBounds(0, 0, size, size)
            d.draw(Canvas(bmp))
        }
    }

    /**
     * Каждый значок и цвет из ресурсов должен открываться на старых версиях.
     * Именно эта проверка ловит «тихие» ошибки drawable, из-за которых приложение падало при запуске.
     */
    @Test
    fun `все картинки приложения открываются на старых версиях`() {
        val ctx = TestSupport.ctx()
        val broken = mutableListOf<String>()
        R.drawable::class.java.fields.forEach { field ->
            val name = field.name
            val res = runCatching { field.getInt(null) }.getOrNull() ?: return@forEach
            val drawable = runCatching { androidx.core.content.ContextCompat.getDrawable(ctx, res) }.getOrNull()
            if (drawable == null) {
                val reason = runCatching { androidx.core.content.ContextCompat.getDrawable(ctx, res) }
                    .exceptionOrNull()?.toString() ?: "null"
                broken += "$name ($reason)"
            }
        }
        assertTrue("не открылись картинки на API ${android.os.Build.VERSION.SDK_INT}: $broken", broken.isEmpty())
    }

    @Test
    fun `все цвета и селекторы открываются на старых версиях`() {
        val ctx = TestSupport.ctx()
        val broken = mutableListOf<String>()
        R.color::class.java.fields.forEach { field ->
            val name = field.name
            val res = runCatching { field.getInt(null) }.getOrNull() ?: return@forEach
            val list = runCatching { androidx.appcompat.content.res.AppCompatResources.getColorStateList(ctx, res) }.getOrNull()
            if (list == null) broken += name
        }
        assertTrue("не открылись цвета на API ${android.os.Build.VERSION.SDK_INT}: $broken", broken.isEmpty())
    }
}
