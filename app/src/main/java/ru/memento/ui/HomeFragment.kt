package ru.memento.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ru.memento.R
import ru.memento.data.Reminder
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.databinding.FragmentHomeBinding
import ru.memento.databinding.ItemWeekChipBinding
import ru.memento.ui.adapter.ReminderAdapter
import java.time.LocalDate

/**
 * Главный экран: приветствие с кольцом прогресса и виджеты — «Сегодня», «Следующее»,
 * «Прогресс дня», «Ближайшая неделя», «Расписание на день», «Агент».
 */
class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private lateinit var store: Store
    private lateinit var todayAdapter: ReminderAdapter
    private lateinit var dayAdapter: ReminderAdapter
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            b.homeClock.text = "%02d:%02d".format(java.time.LocalTime.now().hour, java.time.LocalTime.now().minute)
            ticker.postDelayed(this, 20_000)
        }
    }

    private val listener: () -> Unit = { refresh() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        store = Store.get(requireContext())

        todayAdapter = ReminderAdapter(
            modeProvider = { modeFor(store.settings.widgetLayout) },
            onToggle = { r, done -> toggle(r, done) },
            onOpen = { openSheet(it) },
            onPlus10 = { shift(it, 10) },
        )
        dayAdapter = ReminderAdapter(
            modeProvider = { ReminderAdapter.Mode.TIMELINE },
            onToggle = { r, done -> toggle(r, done) },
            onOpen = { openSheet(it) },
            onPlus10 = { shift(it, 10) },
        )
        // 4×4 на главной показывает «Расписание на день», остальные размеры — тот же список,
        // но с другим видом строки (компактный/стандартный).
        b.homeTodayList.layoutManager = LinearLayoutManager(requireContext())
        b.homeTodayList.adapter = if (store.settings.widgetLayout == "4x4") dayAdapter else todayAdapter
        b.homeDayList.layoutManager = LinearLayoutManager(requireContext())
        b.homeDayList.adapter = dayAdapter

        b.homeAssistant.setOnClickListener { (activity as? MainActivity)?.openAssistant() }
        b.homeAgentBtn.setOnClickListener { (activity as? MainActivity)?.openAssistant() }
        b.homeWidgetsLink.setOnClickListener { (activity as? MainActivity)?.show(MainActivity.TAB_WIDGETS) }
        b.homeTodayHeader.setOnClickListener { (activity as? MainActivity)?.show(MainActivity.TAB_WIDGETS) }
        b.homeNextChip.setOnClickListener { (activity as? MainActivity)?.show(MainActivity.TAB_ALERTS) }

        store.addListener(listener)
        ticker.post(tick)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun toggle(r: Reminder, done: Boolean) {
        store.toggleDone(r.id, done)
        if (done) store.addNotification(r.title, "Выполнено", r.id, ru.memento.data.NotifKind.DONE)
        store.changed()
        refresh()
    }

    private fun shift(r: Reminder, minutes: Int) {
        r.time = Ru.minutesToTime(Ru.timeToMinutes(r.time) + minutes)
        r.updatedAt = System.currentTimeMillis()
        store.upsert(r)
        store.changed()
        refresh()
    }

    private fun openSheet(r: Reminder) {
        startActivity(ReminderSheetActivity.editIntent(requireContext(), r.id))
    }

    private fun refresh() {
        val ctx = requireContext()
        val today = store.today()
        val (done, total) = store.todayStats()
        val left = today.count { !it.done }
        val now = java.time.LocalTime.now()

        b.homeDay.text = "${Ru.fmtLongDate(LocalDate.now())}, ${Ru.eventsWord(today.size)}"
        tick.run()

        b.homeGreeting.text = "${Ru.greeting(now.hour)},\n${store.settings.userName}"
        b.homeSub.text = Ru.greetingSub(now.hour, left)
        val ratio = if (total > 0) done.toFloat() / total else 0f
        b.homeRing.setProgress(ratio)
        b.homeRingText.text = "$done/$total"

        store.lastAgentSummary.takeIf { it.isNotBlank() }?.let { b.homeAssistantSub.text = it }

        val widgets = store.settings.activeWidgets
        b.homeCardToday.visibility = if (widgets.contains("today")) View.VISIBLE else View.GONE
        b.homeCardNext.visibility = if (widgets.contains("next")) View.VISIBLE else View.GONE
        b.homeCardProgress.visibility = if (widgets.contains("progress")) View.VISIBLE else View.GONE
        b.homeCardWeek.visibility = if (widgets.contains("week")) View.VISIBLE else View.GONE
        b.homeCardDay.visibility = if (widgets.contains("day")) View.VISIBLE else View.GONE
        b.homeCardAgent.visibility = if (widgets.contains("agent")) View.VISIBLE else View.GONE

        // Виджет «Сегодня» и «Расписание на день»
        b.homeTodayList.adapter = if (store.settings.widgetLayout == "4x4") dayAdapter else todayAdapter
        val chronological = today.sortedBy { Ru.timeToMinutes(it.time) }
        (if (store.settings.widgetLayout == "4x4") dayAdapter else todayAdapter).submit(chronological)
        if (store.settings.widgetLayout == "4x4") b.homeTodayList.visibility = View.GONE else b.homeTodayList.visibility = View.VISIBLE
        b.homeTodayCount.text = if (today.isEmpty()) ctx.getString(R.string.no_reminders) else Ru.remindersWord(today.size)
        b.homeTodayEmpty.visibility = if (today.isEmpty()) View.VISIBLE else View.GONE
        b.homeDayTotal.text = Ru.remindersWord(left)

        // «Следующее»
        val next = store.next()
        if (next != null) {
            b.homeNextTitle.text = "${next.title} — ${next.time}"
            b.homeNextSub.text = buildString {
                append(Ru.relativeLabel(Ru.triggerMillis(next)))
                if (next.place.isNotBlank()) append(" · ").append(next.place)
            }
            b.homeNextChip.visibility = View.VISIBLE
            b.homeNextChip.text = Ru.dayLabel(next.date)
        } else {
            b.homeNextTitle.text = ctx.getString(R.string.no_reminders)
            b.homeNextSub.text = ctx.getString(R.string.next_none)
            b.homeNextChip.visibility = View.GONE
        }

        // «Прогресс дня»
        b.homeProgressDone.text = done.toString()
        b.homeProgressTotal.text = "/ $total"
        val percent = if (total > 0) done * 100 / total else 0
        b.homeProgressBar.progress = percent
        b.homeProgressRing.setProgress(ratio)
        b.homeProgressPercent.text = "$percent%"

        // «Ближайшая неделя»
        b.homeWeekRow.removeAllViews()
        val weekStart = LocalDate.now()
        for (i in 0 until 7) {
            val date = weekStart.plusDays(i.toLong())
            val chip = ItemWeekChipBinding.inflate(layoutInflater, b.homeWeekRow, false)
            chip.weekDay.text = Ru.WEEK_FULL[date.dayOfWeek.value % 7].take(2)
                .replaceFirstChar { it.uppercase() }
            val count = store.all().count { it.date == Ru.toISO(date) && !it.done }
            chip.weekCount.text = count.toString()
            chip.weekCount.alpha = if (count > 0) 1f else 0.35f
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.marginStart = resources.getDimensionPixelSize(R.dimen.s2)
            chip.root.layoutParams = lp
            chip.root.setOnClickListener {
                (activity as? MainActivity)?.show(MainActivity.TAB_CALENDAR)
            }
            b.homeWeekRow.addView(chip.root)
        }

        // «Агент»
        b.homeAgentText.text = store.lastAgentSummary.ifBlank { ctx.getString(R.string.assistant_sub) }
    }

    /** 2×2 → компактные строки, 4×4 → таймлайн, 4×2 → стандарт. */
    private fun modeFor(layout: String): ReminderAdapter.Mode = when (layout) {
        "2x2" -> ReminderAdapter.Mode.COMPACT
        "4x4" -> ReminderAdapter.Mode.TIMELINE
        else -> ReminderAdapter.Mode.ROW
    }

    override fun onDestroyView() {
        store.removeListener(listener)
        ticker.removeCallbacks(tick)
        super.onDestroyView()
        _b = null
    }
}
