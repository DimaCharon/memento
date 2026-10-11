package ru.memento.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import ru.memento.R
import ru.memento.alarm.AlarmScheduler
import ru.memento.data.Memento
import ru.memento.data.Priority
import ru.memento.data.Reminder
import ru.memento.data.Repeat
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.data.label
import ru.memento.databinding.ActivityReminderSheetBinding
import ru.memento.databinding.DialogTimePickerBinding
import java.time.LocalDate

/**
 * Шторка напоминания: название, дата (чипы + системный календарь), время (колесо),
 * повтор, заметки, место, «за сколько уведомить» и приоритет.
 */
class ReminderSheetActivity : AppCompatActivity() {

    private lateinit var b: ActivityReminderSheetBinding
    private lateinit var store: Store

    private var editing: Reminder? = null
    private var date: String = Ru.todayISO()
    private var time: String = defaultTime()
    private var repeat: Repeat = Repeat.NONE
    private var priority: Priority = Priority.MEDIUM
    private var leadMinutes: Int = 10

    private fun defaultTime(): String {
        val d = java.time.LocalDateTime.now().plusMinutes(30)
        return "%02d:%02d".format(d.hour, d.minute)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityReminderSheetBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = Store.get(this)

        val id = intent.getStringExtra("id")
        editing = store.byId(id)
        editing?.let {
            date = it.date
            time = it.time
            repeat = it.repeat
            priority = it.priority
            leadMinutes = it.leadMinutes
            b.sheetInputTitle.setText(it.title)
            b.sheetNotes.setText(it.notes)
            b.sheetPlace.setText(it.place)
            b.sheetTitle.setText(R.string.edit_reminder)
            b.sheetSave.setText(R.string.save)
            b.sheetDelete.visibility = View.VISIBLE
        }

        ViewCompat.setOnApplyWindowInsetsListener(b.sheetScroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }

        wire()
        render()
    }

    private fun wire() {
        b.sheetCancel.setOnClickListener { finishSheet() }
        b.sheetSaveTop.setOnClickListener { save() }
        b.sheetSave.setOnClickListener { save() }
        b.sheetRoot.setOnClickListener { finishSheet() }
        b.sheetScroll.setOnClickListener { /* перехватываем тап, чтобы не закрывать */ }

        b.sheetDate.setOnClickListener { pickDate() }
        b.sheetTimeRow.setOnClickListener { pickTime() }
        b.sheetRepeatRow.setOnClickListener { pickRepeat() }
        b.sheetLeadRow.setOnClickListener { pickLead() }

        b.sheetChipToday.setOnClickListener { setDate(Ru.todayISO()) }
        b.sheetChipTomorrow.setOnClickListener { setDate(Ru.addDaysISO(Ru.todayISO(), 1)) }
        b.sheetChipPlus2.setOnClickListener { setDate(Ru.addDaysISO(Ru.todayISO(), 2)) }
        b.sheetChipWeek.setOnClickListener { setDate(Ru.addDaysISO(Ru.todayISO(), 7)) }

        b.sheetPrioHigh.setOnClickListener { setPriority(Priority.HIGH) }
        b.sheetPrioMedium.setOnClickListener { setPriority(Priority.MEDIUM) }
        b.sheetPrioLow.setOnClickListener { setPriority(Priority.LOW) }

        b.sheetDelete.setOnClickListener { confirmDelete() }
    }

    private fun render() {
        b.sheetDate.text = Ru.fullDateLabel(date)
        b.sheetTime.text = time
        b.sheetRepeat.text = repeat.label(this)
        b.sheetLead.text = leadLabel(leadMinutes)
        b.sheetChipToday.isSelected = date == Ru.todayISO()
        b.sheetChipTomorrow.isSelected = date == Ru.addDaysISO(Ru.todayISO(), 1)
        b.sheetChipPlus2.isSelected = date == Ru.addDaysISO(Ru.todayISO(), 2)
        b.sheetChipWeek.isSelected = date == Ru.addDaysISO(Ru.todayISO(), 7)
        b.sheetPrioHigh.isSelected = priority == Priority.HIGH
        b.sheetPrioMedium.isSelected = priority == Priority.MEDIUM
        b.sheetPrioLow.isSelected = priority == Priority.LOW
    }

    private fun leadLabel(minutes: Int): String = when (minutes) {
        0 -> getString(R.string.lead_now)
        5 -> getString(R.string.lead_5)
        10 -> getString(R.string.lead_10)
        30 -> getString(R.string.lead_30)
        60 -> getString(R.string.lead_60)
        else -> getString(R.string.lead_10)
    }

    private fun setDate(iso: String) {
        date = iso
        render()
    }

    private fun setPriority(p: Priority) {
        priority = p
        render()
    }

    private fun pickDate() {
        val d = Ru.fromISO(date)
        val dialog = DatePickerDialog(
            this,
            R.style.Theme_Memento,
            { _, year, month, day -> setDate("%04d-%02d-%02d".format(year, month + 1, day)) },
            d.year,
            d.monthValue - 1,
            d.dayOfMonth,
        )
        dialog.show()
    }

    /** Колесо времени как в референсе: два барабана + быстрые кнопки. */
    private fun pickTime() {
        val v = DialogTimePickerBinding.inflate(LayoutInflater.from(this))
        val start = Ru.timeToMinutes(time)
        v.pickHour.minValue = 0
        v.pickHour.maxValue = 23
        v.pickHour.value = (start / 60) % 24
        v.pickMinute.minValue = 0
        v.pickMinute.maxValue = 59
        v.pickMinute.value = start % 60
        v.pickMinute.setFormatter { value -> "%02d".format(value) }
        v.pickHour.setFormatter { value -> "%02d".format(value) }

        val dialog = AlertDialog.Builder(this, R.style.Theme_Memento)
            .setView(v.root)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog)

        fun shift(delta: Int) {
            val total = ((v.pickHour.value * 60 + v.pickMinute.value + delta) % 1440 + 1440) % 1440
            v.pickHour.value = total / 60
            v.pickMinute.value = total % 60
        }
        v.pickPlus10.setOnClickListener { shift(10) }
        v.pickPlus30.setOnClickListener { shift(30) }
        v.pickPlus60.setOnClickListener { shift(60) }
        v.pickCancel.setOnClickListener { dialog.dismiss() }
        v.pickOk.setOnClickListener {
            time = "%02d:%02d".format(v.pickHour.value, v.pickMinute.value)
            dialog.dismiss()
            render()
        }
        dialog.show()
    }

    private fun pickRepeat() {
        val options = Repeat.entries.map { it.label(this) }.toTypedArray()
        AlertDialog.Builder(this, R.style.Theme_Memento)
            .setTitle(R.string.repeat)
            .setItems(options) { _, which ->
                repeat = Repeat.entries[which]
                render()
            }
            .show()
    }

    private fun pickLead() {
        val values = listOf(0, 5, 10, 30, 60)
        val options = values.map { leadLabel(it) }.toTypedArray()
        AlertDialog.Builder(this, R.style.Theme_Memento)
            .setTitle(R.string.lead)
            .setItems(options) { _, which ->
                leadMinutes = values[which]
                render()
            }
            .show()
    }

    private fun save() {
        val title = b.sheetInputTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            b.sheetInputTitle.error = getString(R.string.field_title)
            b.sheetInputTitle.requestFocus()
            return
        }
        val reminder = editing ?: Reminder(id = Store.uid("rem"), title = title, date = date, time = time)
        reminder.title = title
        reminder.date = date
        reminder.time = time
        reminder.repeat = repeat
        reminder.priority = priority
        reminder.leadMinutes = leadMinutes
        reminder.notes = b.sheetNotes.text?.toString()?.trim().orEmpty()
        reminder.place = b.sheetPlace.text?.toString()?.trim().orEmpty()
        reminder.icon = if (reminder.icon.isBlank()) "bell" else reminder.icon
        reminder.done = false
        reminder.firedAt = null
        reminder.snoozedUntil = null
        if (editing == null) reminder.source = "Вручную"

        store.upsert(reminder)
        AlarmScheduler.scheduleReminder(this, reminder)
        store.changed()
        Toast.makeText(this, R.string.reminder_saved, Toast.LENGTH_SHORT).show()
        finishSheet()
    }

    private fun confirmDelete() {
        val r = editing ?: return
        AlertDialog.Builder(this, R.style.Theme_Memento)
            .setTitle(R.string.delete_q)
            .setPositiveButton(R.string.yes_delete) { _, _ ->
                AlarmScheduler.cancel(this, r)
                store.delete(r.id)
                store.changed()
                Toast.makeText(this, R.string.reminder_deleted, Toast.LENGTH_SHORT).show()
                finishSheet()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun finishSheet() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.fade_in, R.anim.sheet_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.sheet_out)
        }
    }

    companion object {
        const val ACTION_NEW = "ru.memento.action.NEW_REMINDER"
        const val ACTION_EDIT = "ru.memento.action.EDIT_REMINDER"

        fun editIntent(context: Context, id: String): Intent =
            Intent(context, ReminderSheetActivity::class.java).apply {
                action = ACTION_EDIT
                putExtra("id", id)
            }
    }
}
