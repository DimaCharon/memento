package ru.memento.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.memento.R
import ru.memento.data.Priority
import ru.memento.data.Reminder
import ru.memento.data.Ru
import ru.memento.data.dotRes
import ru.memento.databinding.ItemReminderCompactBinding
import ru.memento.databinding.ItemReminderRowBinding
import ru.memento.databinding.ItemReminderTimelineBinding

/**
 * Список напоминаний в трёх видах — как размеры виджета на сайте:
 * компактно (2×2), стандарт (4×2) и расписание-таймлайн (4×4).
 */
class ReminderAdapter(
    private val modeProvider: () -> Mode,
    private val onToggle: (Reminder, Boolean) -> Unit,
    private val onOpen: (Reminder) -> Unit,
    private val onPlus10: (Reminder) -> Unit,
) : RecyclerView.Adapter<ReminderAdapter.VH>() {

    enum class Mode { COMPACT, ROW, TIMELINE }

    private val items = ArrayList<Reminder>()

    fun submit(list: List<Reminder>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = modeProvider().ordinal

    class VH(val view: View, val bind: (Reminder, Int) -> Unit) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return when (Mode.entries[viewType]) {
            Mode.COMPACT -> {
                val b = ItemReminderCompactBinding.inflate(inflater, parent, false)
                VH(b.root) { r, _ ->
                    b.rowTime.text = r.time
                    b.rowTitle.text = r.title
                    b.rowTitle.alpha = if (r.done) 0.45f else 1f
                    b.rowTime.alpha = if (r.done) 0.45f else 1f
                    b.rowCheck.isChecked = r.done
                    b.rowCheck.setOnClickListener { onToggle(r, !r.done) }
                    b.rowCheck.setOnCheckedChangeListener(null)
                    b.rowRoot.setOnClickListener { onOpen(r) }
                }
            }

            Mode.ROW -> {
                val b = ItemReminderRowBinding.inflate(inflater, parent, false)
                VH(b.root) { r, _ ->
                    b.rowTime.text = r.time
                    b.rowTitle.text = r.title
                    b.rowPlace.text = r.place.ifBlank { r.notes }
                    b.rowPlace.visibility = if (b.rowPlace.text.isNullOrBlank()) View.GONE else View.VISIBLE
                    val dim = if (r.done) 0.45f else 1f
                    b.rowTime.alpha = dim
                    b.rowTitle.alpha = dim
                    b.rowPlace.alpha = dim
                    b.rowDot.setBackgroundResource(r.priority.dotRes())
                    b.rowDot.alpha = dim
                    b.rowCheck.isChecked = r.done
                    b.rowCheck.setOnClickListener { onToggle(r, !r.done) }
                    b.rowRoot.setOnClickListener { onOpen(r) }
                }
            }

            Mode.TIMELINE -> {
                val b = ItemReminderTimelineBinding.inflate(inflater, parent, false)
                VH(b.root) { r, _ ->
                    b.rowTime.text = r.time
                    b.rowTitle.text = r.title
                    b.rowTitle.alpha = if (r.done) 0.45f else 1f
                    b.rowPlace.text = buildString {
                        append(Ru.relativeLabel(Ru.triggerMillis(r)))
                        if (r.place.isNotBlank()) append(" · ").append(r.place)
                    }
                    b.rowPlus10.text = "+10 мин"
                    b.rowPlus10.setOnClickListener { onPlus10(r) }
                    b.rowEdit.text = b.root.context.getString(R.string.edit_reminder)
                    b.rowEdit.setOnClickListener { onOpen(r) }
                    b.rowDone.text = if (r.done) b.root.context.getString(R.string.undo) else b.root.context.getString(R.string.mark_done)
                    b.rowDone.setOnClickListener { onToggle(r, !r.done) }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position)
    }
}
