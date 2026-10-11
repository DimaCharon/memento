package ru.memento.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ru.memento.R
import ru.memento.data.Reminder
import ru.memento.data.Ru
import ru.memento.data.dotRes
import ru.memento.databinding.ItemDayCellBinding
import java.time.LocalDate

/**
 * Сетка месяца 7×N: точка-маркер на днях с делами, подсветка сегодня и выбранного дня.
 * Ячейки одинаковые, поэтому колонки и строки стоят ровно.
 */
class CalendarAdapter(
    private val monthProvider: () -> LocalDate,
    private val daysProvider: () -> List<LocalDate>,
    private val onPick: (LocalDate) -> Unit,
) : RecyclerView.Adapter<CalendarAdapter.VH>() {

    var reminders: List<Reminder> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var selected: LocalDate? = LocalDate.now()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class VH(val view: View, val bind: (LocalDate) -> Unit) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemDayCellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b.root) { date ->
            val ctx = b.root.context
            val inMonth = date.monthValue == monthProvider().monthValue && date.year == monthProvider().year
            val isToday = date == LocalDate.now()
            val isSelected = date == selected

            b.dayNumber.text = date.dayOfMonth.toString()
            b.dayNumber.alpha = if (inMonth) 1f else 0.32f
            b.dayNumber.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (isSelected) R.color.txt else if (isToday) R.color.accentStrong else R.color.txt2,
                ),
            )
            b.dayRoot.isSelected = isSelected
            b.dayRoot.alpha = if (inMonth) 1f else 0.6f

            // Точки-маркеры: до трёх, с приоритетным цветом
            b.dayDots.removeAllViews()
            val dots = reminders.filter { it.date == Ru.toISO(date) && !it.done }.take(3)
            dots.forEach { r ->
                val dot = View(ctx)
                val size = ctx.resources.getDimensionPixelSize(R.dimen.s1)
                val lp = LinearLayout.LayoutParams(size, size)
                lp.marginStart = if (b.dayDots.childCount > 0) size / 2 else 0
                dot.layoutParams = lp
                dot.background = ContextCompat.getDrawable(ctx, r.priority.dotRes())
                b.dayDots.addView(dot)
            }
            b.dayRoot.setOnClickListener { onPick(date) }
        }
    }

    override fun getItemCount(): Int = daysProvider().size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(daysProvider()[position])
}
