package ru.memento.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import ru.memento.R
import ru.memento.data.WidgetDef
import ru.memento.databinding.ItemWidgetToggleBinding

/** Список виджетов с тумблерами (как на сайте). */
class WidgetAdapter(private val onToggle: (WidgetDef, Boolean) -> Unit) :
    RecyclerView.Adapter<WidgetAdapter.VH>() {

    private val items: List<WidgetDef> = ru.memento.data.WIDGET_DEFS

    class VH(val view: View, val bind: (WidgetDef) -> Unit) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemWidgetToggleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b.root) { w ->
            b.wgTitle.text = w.title
            b.wgCaption.text = w.caption + " · " + w.size
            b.wgIcon.setImageResource(iconFor(w.id))
            b.wgSwitch.isChecked = ru.memento.data.Store.get(b.root.context).settings.activeWidgets.contains(w.id)
            b.wgSwitch.setOnClickListener(null)
            b.wgSwitch.setOnCheckedChangeListener { _, checked -> onToggle(w, checked) }
            b.wgRoot.setOnClickListener { b.wgSwitch.isChecked = !b.wgSwitch.isChecked }
        }
    }

    private fun iconFor(id: String): Int = when (id) {
        "today" -> R.drawable.ic_calendar
        "next" -> R.drawable.ic_clock
        "progress" -> R.drawable.ic_target
        "week" -> R.drawable.ic_week
        "day" -> R.drawable.ic_list
        else -> R.drawable.ic_sparkle
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}
