package ru.memento.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ru.memento.R
import ru.memento.data.Reminder
import ru.memento.data.Ru
import ru.memento.data.Store
import ru.memento.databinding.FragmentRemindersBinding
import ru.memento.notify.Notifier
import ru.memento.ui.adapter.NotificationAdapter
import ru.memento.ui.adapter.ReminderAdapter

/**
 * «Напоминания»: активные дела с кнопками и центр уведомлений (история сработавших карточек).
 */
class RemindersFragment : Fragment() {

    private var _b: FragmentRemindersBinding? = null
    private val b get() = _b!!
    private lateinit var store: Store
    private lateinit var active: ReminderAdapter
    private lateinit var history: NotificationAdapter
    private var tab = "active"
    private var filter = "all"

    private val listener: () -> Unit = { refresh() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _b = FragmentRemindersBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        store = Store.get(requireContext())

        active = ReminderAdapter(
            modeProvider = { ReminderAdapter.Mode.TIMELINE },
            onToggle = { r, done -> complete(r, done) },
            onOpen = { startActivity(ReminderSheetActivity.editIntent(requireContext(), it.id)) },
            onPlus10 = { r -> snooze(r, 10) },
        )
        b.remList.layoutManager = LinearLayoutManager(requireContext())

        history = NotificationAdapter(
            store = store,
            onSnooze = { r -> snooze(r, 10) },
            onDone = { r -> complete(r, true) },
            onOpen = { startActivity(ReminderSheetActivity.editIntent(requireContext(), it.id)) },
        )

        b.remTabActive.setOnClickListener { switchTab("active") }
        b.remTabHistory.setOnClickListener { switchTab("history") }
        b.remFilterAll.setOnClickListener { setFilter("all") }
        b.remFilterOverdue.setOnClickListener { setFilter("overdue") }
        b.remFilterDone.setOnClickListener { setFilter("done") }
        b.histRead.setOnClickListener {
            store.markAllRead()
            store.changed(reschedule = false)
            refresh()
        }
        b.histClear.setOnClickListener {
            store.clearNotifications()
            store.changed(reschedule = false)
            refresh()
        }

        store.addListener(listener)
        refresh()
    }

    private fun switchTab(value: String) {
        tab = value
        b.remTabActive.isSelected = value == "active"
        b.remTabHistory.isSelected = value == "history"
        refresh()
    }

    private fun setFilter(value: String) {
        filter = value
        b.remFilterAll.isSelected = value == "all"
        b.remFilterOverdue.isSelected = value == "overdue"
        b.remFilterDone.isSelected = value == "done"
        refresh()
    }

    private fun complete(r: Reminder, done: Boolean) {
        store.toggleDone(r.id, done)
        if (done) {
            store.addNotification(r.title, "Выполнено", r.id, ru.memento.data.NotifKind.DONE)
            Notifier.cancelReminder(requireContext(), r)
        }
        store.changed()
        refresh()
        android.widget.Toast.makeText(
            requireContext(),
            if (done) getString(R.string.reminder_done_toast) else getString(R.string.undo),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    private fun snooze(r: Reminder, minutes: Int) {
        store.snooze(r.id, minutes)
        store.addNotification(r.title, "Отложено на $minutes мин", r.id, ru.memento.data.NotifKind.SNOOZE)
        Notifier.cancelReminder(requireContext(), r)
        store.changed()
        refresh()
        android.widget.Toast.makeText(
            requireContext(),
            getString(R.string.snoozed_until, Ru.clockOf(Ru.triggerMillis(r))),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    private fun refresh() {
        val all = store.all()
        val activeCount = all.count { !it.done }
        b.remCounter.text = activeCount.toString()

        val showActive = tab == "active"
        b.remFilters.visibility = if (showActive) View.VISIBLE else View.GONE
        b.histHeader.visibility = if (showActive) View.GONE else View.VISIBLE
        b.remList.adapter = if (showActive) active else history

        if (showActive) {
            val now = System.currentTimeMillis()
            val items = all.filter {
                when (filter) {
                    "done" -> it.done
                    "overdue" -> !it.done && Ru.reminderMillis(it) < now
                    else -> !it.done
                }
            }.sortedWith(compareBy({ it.date }, { Ru.timeToMinutes(it.time) }))
            active.submit(items)
            b.remEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            b.remEmpty.text = when (filter) {
                "done" -> getString(R.string.filter_done) + " — " + getString(R.string.no_reminders).lowercase()
                "overdue" -> getString(R.string.overdue) + " — " + getString(R.string.nothing_today).lowercase()
                else -> getString(R.string.nothing_today)
            }
        } else {
            // История: уведомления, которые уже сработали
            val list = store.notifications
            history.submit(list.ifEmpty {
                store.reminders.filter { it.done }.map {
                    ru.memento.data.NotificationItem(
                        id = it.id, reminderId = it.id, title = it.title,
                        body = "Выполнено", at = it.updatedAt, kind = ru.memento.data.NotifKind.DONE, read = true,
                    )
                }
            })
            b.remEmpty.visibility = if (store.notifications.isEmpty()) View.VISIBLE else View.GONE
            b.remEmpty.text = getString(R.string.notif_empty) + "\n" + getString(R.string.notif_empty_hint)
        }
    }

    override fun onDestroyView() {
        store.removeListener(listener)
        super.onDestroyView()
        _b = null
    }
}
