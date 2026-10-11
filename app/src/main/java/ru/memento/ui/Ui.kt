package ru.memento.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView

/** Мелочи, которые делают разметку одинаковой во всех экранах. */

/** Плавная прокрутка списка к последнему элементу (диалог, журнал). */
fun RecyclerView.scrollToBottom() {
    if (adapter == null) return
    val count = adapter?.itemCount ?: 0
    if (count > 0) post { scrollToPosition(count - 1) }
}

/** Иконка в цвете из ресурсов. */
fun ImageView.tint(colorRes: Int) {
    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(ContextCompat.getColor(context, colorRes)))
}

fun View.color(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)
