package ru.memento.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.memento.R
import ru.memento.data.Profile
import ru.memento.databinding.ItemProfileBinding

/** Несколько профилей на одном телефоне. */
class ProfileAdapter(private val onPick: (Profile) -> Unit) : RecyclerView.Adapter<ProfileAdapter.VH>() {

    private val items = ArrayList<Profile>()
    private var activeId: String = ""

    fun submit(list: List<Profile>, active: String) {
        items.clear()
        items.addAll(list)
        activeId = active
        notifyDataSetChanged()
    }

    class VH(val view: View, val bind: (Profile) -> Unit) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b.root) { p ->
            b.prAvatar.text = p.name.take(1).uppercase()
            b.prName.text = p.name
            val active = p.id == activeId
            b.prBadge.visibility = if (active) View.VISIBLE else View.GONE
            b.prRoot.isSelected = active
            b.prRoot.setOnClickListener { onPick(p) }
        }
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}
