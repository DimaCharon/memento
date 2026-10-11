package ru.memento.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.memento.R
import ru.memento.data.NotificationItem
import ru.memento.data.NotifKind
import ru.memento.data.Reminder
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.data.dotRes
import ru.memento.data.iconResFor
import ru.memento.databinding.ItemNotificationBinding

/** Центр уведомлений: карточки «Memento · Через 23 мин» с кнопками «Отложить» и «Выполнено». */
class NotificationAdapter(
    private val store: Store,
    private val onSnooze: (Reminder) -> Unit,
    private val onDone: (Reminder) -> Unit,
    private val onOpen: (Reminder) -> Unit,
) : RecyclerView.Adapter<NotificationAdapter.VH>() {

    private val items = ArrayList<NotificationItem>()

    fun submit(list: List<NotificationItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val view: View, val bind: (NotificationItem) -> Unit) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b.root) { n ->
            val r = store.byId(n.reminderId)
            b.ntfTitle.text = n.title
            b.ntfBody.text = n.body
            b.ntfWhen.text = b.root.context.getString(
                R.string.notif_in,
                Ru.relativeLabel(n.at),
            )
            b.ntfIcon.setImageResource(iconResFor(r?.icon))
            b.ntfDot.setBackgroundResource((r?.priority ?: ru.memento.data.Priority.MEDIUM).dotRes())
            b.ntfDot.visibility = if (n.read) View.INVISIBLE else View.VISIBLE
            b.ntfWhen.alpha = if (n.read) 0.6f else 1f
            b.ntfActions.visibility = if (n.kind == NotifKind.DONE || r?.done == true) View.GONE else View.VISIBLE
            b.ntfSnooze.setOnClickListener { r?.let(onSnooze) }
            b.ntfDone.setOnClickListener { r?.let(onDone) }
            b.ntfRoot.setOnClickListener { r?.let(onOpen) }
        }
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}
