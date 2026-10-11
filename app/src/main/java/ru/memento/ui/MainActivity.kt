package ru.memento.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import ru.memento.CrashGuard
import ru.memento.R
import ru.memento.databinding.ActivityMainBinding
import ru.memento.data.Memento
import ru.memento.data.Store
import ru.memento.notify.Notifier

/**
 * Главный экран: четыре вкладки, нижняя навигация и центральная кнопка «+».
 * Все элементы выровнены по сетке 4dp, кнопка «+» — ровно по центру экрана.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var currentTab = TAB_HOME
    private var themeRes = R.style.Theme_Memento

    private fun theTheme(): Int {
        val name = runCatching { Store.get(this).settings.theme }.getOrDefault("graphite")
        return if (name == "coal") R.style.Theme_Memento_Coal else R.style.Theme_Memento
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тему нужно выбрать до super.onCreate — иначе на части прошивок это приводит к сбою
        themeRes = theTheme()
        setTheme(themeRes)
        super.onCreate(savedInstanceState)

        // Прошлый запуск закончился сбоем — сначала покажем понятное сообщение с текстом ошибки
        val report = runCatching { CrashGuard.pendingReport(this) }.getOrNull()
        if (report != null && !intent.getBooleanExtra(EXTRA_FROM_CRASH, false)) {
            startActivity(CrashActivity.intent(this, report))
            finish()
            return
        }

        runCatching { CrashGuard.install(applicationContext) }

        try {
            buildUi(savedInstanceState)
        } catch (t: Throwable) {
            // Интерфейс не собрался на этом телефоне — покажем ошибку, а не «произошёл сбой»
            runCatching {
                startActivity(CrashActivity.intent(this, describe(t)))
                finish()
            }
        }
    }

    private fun describe(t: Throwable): String {
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        return "Memento ${ru.memento.BuildConfig.VERSION_NAME}\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n\n" +
            sw.toString()
    }

    private fun buildUi(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        Notifier.ensureChannels(this)
        applyInsets()
        wireNav()

        if (savedInstanceState == null) {
            val tab = intent?.getStringExtra(EXTRA_OPEN_TAB) ?: TAB_HOME
            show(tab, animate = false)
        } else {
            currentTab = savedInstanceState.getString(KEY_TAB, TAB_HOME)
            selectNav(if (currentTab == TAB_ASSISTANT) null else currentTab)
        }

        requestNotificationPermissionIfNeeded()
        handleIntent(intent)
        Memento.sync(this)
    }

    // ------------------------------- системные отступы -------------------------------

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.content.updatePadding(top = bars.top)
            b.navHost.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                bottomMargin = dimen(R.dimen.nav_bottom) + bars.bottom
            }
            b.fab.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                bottomMargin = dimen(R.dimen.fab_margin_bottom) + bars.bottom
            }
            insets
        }
        ViewCompat.requestApplyInsets(b.root)
    }

    private fun dimen(id: Int): Int = resources.getDimensionPixelSize(id)

    // ------------------------------- навигация -------------------------------

    private fun wireNav() {
        b.navBar.navHome.setOnClickListener { show(TAB_HOME) }
        b.navBar.navCalendar.setOnClickListener { show(TAB_CALENDAR) }
        b.navBar.navAlerts.setOnClickListener { show(TAB_ALERTS) }
        b.navBar.navProfile.setOnClickListener { show(TAB_PROFILE) }
        // Центральная ячейка панели делает то же, что и «+» (кнопка лежит поверх неё)
        b.navBar.navCenter.setOnClickListener { openNewReminder() }

        b.fab.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            openNewReminder()
        }
        b.fab.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            show(TAB_ASSISTANT)
            true
        }
    }

    private fun openNewReminder() {
        startActivity(
            Intent(this, ReminderSheetActivity::class.java).apply { action = ReminderSheetActivity.ACTION_NEW },
        )
        overridePendingTransition(R.anim.sheet_in, R.anim.fade_in)
    }

    fun show(tab: String, animate: Boolean = true) {
        currentTab = tab
        val tag = "screen-$tab"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        fm.commit {
            if (animate) setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            fm.fragments.forEach { if (it.tag != tag) hide(it) }
            if (existing != null) {
                show(existing)
            } else {
                add(R.id.content, newFragment(tab), tag)
            }
            setReorderingAllowed(true)
        }
        selectNav(if (tab == TAB_ASSISTANT) null else tab)
    }

    private fun newFragment(tab: String): Fragment = when (tab) {
        TAB_CALENDAR -> CalendarFragment()
        TAB_ALERTS -> RemindersFragment()
        TAB_PROFILE -> ProfileFragment()
        TAB_ASSISTANT -> AssistantFragment()
        TAB_WIDGETS -> WidgetsFragment()
        else -> HomeFragment()
    }

    /** Подсветка активной вкладки — только одна, иконки ровно под подписями. */
    private fun selectNav(selected: String?) {
        val items = listOf(
            TAB_HOME to Triple(b.navBar.navHome, b.navBar.navHomeIcon, b.navBar.navHomeLabel),
            TAB_CALENDAR to Triple(b.navBar.navCalendar, b.navBar.navCalendarIcon, b.navBar.navCalendarLabel),
            TAB_ALERTS to Triple(b.navBar.navAlerts, b.navBar.navAlertsIcon, b.navBar.navAlertsLabel),
            TAB_PROFILE to Triple(b.navBar.navProfile, b.navBar.navProfileIcon, b.navBar.navProfileLabel),
        )
        items.forEach { (tab, views) ->
            val on = tab == selected
            views.first.isSelected = on
            (views.second as ImageView).isSelected = on
            (views.third as TextView).isSelected = on
        }
    }

    fun openAssistant() = show(TAB_ASSISTANT)

    // ------------------------------- входящие намерения -------------------------------

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_OPEN_REMINDER -> {
                val id = intent.getStringExtra("id") ?: return
                startActivity(
                    Intent(this, ReminderSheetActivity::class.java).apply {
                        action = ReminderSheetActivity.ACTION_EDIT
                        putExtra("id", id)
                    },
                )
            }
            else -> intent.getStringExtra(EXTRA_OPEN_TAB)?.let { if (currentTab != it) show(it) }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val asked = getSharedPreferences("memento", MODE_PRIVATE).getBoolean("asked_notifications", false)
        if (!granted && !asked) {
            getSharedPreferences("memento", MODE_PRIVATE).edit().putBoolean("asked_notifications", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, currentTab)
    }

    override fun onResume() {
        super.onResume()
        // Экран построился — значит запуск удался: снимаем метку сбоя
        runCatching { CrashGuard.markHealthy(this) }
        // Тема могла смениться в профиле — тогда аккуратно пересоздаём экран
        val want = theTheme()
        if (want != themeRes) {
            themeRes = want
            recreate()
        }
    }

    companion object {
        const val TAB_HOME = "home"
        const val TAB_CALENDAR = "calendar"
        const val TAB_ALERTS = "reminders"
        const val TAB_PROFILE = "profile"
        const val TAB_ASSISTANT = "assistant"
        const val TAB_WIDGETS = "widgets"

        const val ACTION_OPEN_REMINDER = "ru.memento.action.OPEN_REMINDER"
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_FROM_CRASH = "from_crash"
        private const val KEY_TAB = "tab"
    }
}
