package ru.memento.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.memento.data.ChatMessage
import ru.memento.databinding.ItemChatAgentBinding
import ru.memento.databinding.ItemChatUserBinding

/** Диалог с ассистентом: реплики пользователя светлые, ответы агента — стеклянные. */
class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {

    private val items = ArrayList<ChatMessage>()

    fun add(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    fun submit(list: List<ChatMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val view: View, val bind: (ChatMessage) -> Unit) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int): Int = if (items[position].role == "user") 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) {
            val b = ItemChatUserBinding.inflate(inflater, parent, false)
            VH(b.root) { m -> b.chatText.text = m.text }
        } else {
            val b = ItemChatAgentBinding.inflate(inflater, parent, false)
            VH(b.root) { m ->
                b.chatText.text = m.text
                b.chatAgent.text = m.agent ?: "Memento Orchestrator"
            }
        }
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}
