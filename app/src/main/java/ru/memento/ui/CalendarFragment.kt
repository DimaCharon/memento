package ru.memento.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import ru.memento.R
import ru.memento.data.Reminder
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.databinding.FragmentCalendarBinding
import ru.memento.ui.adapter.CalendarAdapter
import ru.memento.ui.adapter.ReminderAdapter
import java.time.LocalDate

/** Календарь: месяц, точки-события, фильтры и список выбранного дня. */
class CalendarFragment : Fragment() {

    private var _b: FragmentCalendarBinding? = null
    private val b get() = _b!!
    private lateinit var store: Store
    private lateinit var grid: CalendarAdapter
    private lateinit var list: ReminderAdapter

    private var month: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var selected: LocalDate = LocalDate.now()
    private var filter = "all"

    private val listener: () -> Unit = { refresh() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _b = FragmentCalendarBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        store = Store.get(requireContext())

        grid = CalendarAdapter(
            monthProvider = { month },
            daysProvider = { monthCells() },
            onPick = { date ->
                selected = date
                if (date.monthValue != month.monthValue || date.year != month.year) {
                    month = date.withDayOfMonth(1)
                    grid.notifyDataSetChanged()
                }
                grid.selected = date
                refresh()
            },
        )
        b.calGrid.layoutManager = GridLayoutManager(requireContext(), 7)
        b.calGrid.adapter = grid

        list = ReminderAdapter(
            modeProvider = { ReminderAdapter.Mode.ROW },
            onToggle = { r, done ->
                store.toggleDone(r.id, done)
                store.changed()
                refresh()
            },
            onOpen = { startActivity(ReminderSheetActivity.editIntent(requireContext(), it.id)) },
            onPlus10 = { },
        )
        b.calList.layoutManager = LinearLayoutManager(requireContext())
        b.calList.adapter = list

        b.calPrev.setOnClickListener {
            month = month.minusMonths(1)
            grid.notifyDataSetChanged()
            refresh()
        }
        b.calNext.setOnClickListener {
            month = month.plusMonths(1)
            grid.notifyDataSetChanged()
            refresh()
        }
        b.calFilterAll.setOnClickListener { setFilter("all") }
        b.calFilterActive.setOnClickListener { setFilter("active") }
        b.calFilterDone.setOnClickListener { setFilter("done") }

        buildWeekHeader()
        store.addListener(listener)
        refresh()
    }

    /** Пн…Вс: ровные колонки одинаковой ширины. */
    private fun buildWeekHeader() {
        val row = b.calWeekHeader
        row.removeAllViews()
        Ru.WEEK_FULL.drop(1).plus(Ru.WEEK_FULL.first()).forEach { day ->
            val tv = TextView(requireContext())
            tv.text = day.take(2).replaceFirstChar { it.uppercase() }
            tv.setTextColor(requireContext().getColor(R.color.txt3))
            tv.textSize = 12f
            tv.gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.layoutParams = lp
            row.addView(tv)
        }
    }

    /** 42 ячейки: с понедельника недели, в которую попал первый день месяца. */
    private fun monthCells(): List<LocalDate> {
        val first = month.withDayOfMonth(1)
        val offset = first.dayOfWeek.value - 1
        val start = first.minusDays(offset.toLong())
        return (0 until 42).map { start.plusDays(it.toLong()) }
    }

    private fun setFilter(value: String) {
        filter = value
        b.calFilterAll.isSelected = value == "all"
        b.calFilterActive.isSelected = value == "active"
        b.calFilterDone.isSelected = value == "done"
        refresh()
    }

    private fun refresh() {
        val all = store.all()
        grid.reminders = all
        grid.selected = selected
        b.calTitle.text = "${Ru.MONTHS_NOM[month.monthValue - 1]} ${month.year}"
        b.calDayLabel.text = Ru.dayLabel(Ru.toISO(selected))

        val dayItems = all.filter { it.date == Ru.toISO(selected) }.filter {
            when (filter) {
                "active" -> !it.done
                "done" -> it.done
                else -> true
            }
        }.sortedBy { Ru.timeToMinutes(it.time) }

        list.submit(dayItems)
        b.calEmpty.visibility = if (dayItems.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        store.removeListener(listener)
        super.onDestroyView()
        _b = null
    }
}
