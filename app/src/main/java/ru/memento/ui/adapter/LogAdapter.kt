package ru.memento.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.memento.R
import ru.memento.data.LogEntry
import ru.memento.data.Ru
import ru.memento.databinding.ItemLogBinding

/** Журнал прогона: видно каждый шаг пяти агентов. */
class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private val items = ArrayList<LogEntry>()

    fun submit(list: List<LogEntry>) {
        items.clear()
        items.addAll(list.takeLast(60))
        notifyDataSetChanged()
    }

    class VH(val view: View, val bind: (LogEntry) -> Unit) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b.root) { l ->
            b.logAgent.text = l.agent
            b.logText.text = l.text
            b.logTime.text = Ru.clockOf(l.at)
            val color = when (l.status) {
                "ok" -> R.color.ok
                "warn" -> R.color.warn
                "err" -> R.color.err
                else -> R.color.accentStrong
            }
            b.logDot.background?.mutate()?.setTint(b.root.context.getColor(color))
        }
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}
