package ru.memento.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import ru.memento.R

/**
 * Круглая галочка как на макете: тонкое кольцо в покое и зелёная заливка с птичкой,
 * когда дело выполнено. Своя отрисовка — одинаково выглядит на любой прошивке.
 */
class MementoCheck @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var listener: ((Boolean) -> Unit)? = null
    private var checked = false

    var isChecked: Boolean
        get() = checked
        set(value) {
            if (checked == value) {
                sync()
                return
            }
            checked = value
            sync()
            listener?.invoke(value)
        }

    init {
        scaleType = ScaleType.FIT_CENTER
        setBackgroundResource(R.drawable.bg_ripple_round)
        isClickable = true
        isFocusable = true
        sync()
        super.setOnClickListener { isChecked = !isChecked }
    }

    fun setOnCheckedChangeListener(l: ((Boolean) -> Unit)?) {
        listener = l
    }

    fun toggle() {
        isChecked = !isChecked
    }

    private fun sync() {
        setImageResource(if (checked) R.drawable.bg_check_on else R.drawable.bg_check_off)
        contentDescription = context.getString(R.string.mark_done)
    }
}
