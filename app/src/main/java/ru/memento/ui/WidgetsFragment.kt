package ru.memento.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ru.memento.R
import ru.memento.data.Store
import ru.memento.databinding.FragmentWidgetsBinding
import ru.memento.ui.adapter.WidgetAdapter
import ru.memento.data.WidgetDef
import ru.memento.widget.Widgets

/**
 * «Виджеты»: переключатель размера «Сегодня» (2×2 / 4×2 / 4×4), тумблеры всех виджетов
 * и кнопки «Добавить на рабочий стол».
 */
class WidgetsFragment : Fragment() {

    private var _b: FragmentWidgetsBinding? = null
    private val b get() = _b!!
    private lateinit var store: Store
    private lateinit var adapter: WidgetAdapter

    private val listener: () -> Unit = { refresh() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _b = FragmentWidgetsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        store = Store.get(requireContext())
        adapter = WidgetAdapter(
            onToggle = { def, checked ->
                if (checked) store.settings.activeWidgets.add(def.id)
                else store.settings.activeWidgets.remove(def.id)
                store.changed(reschedule = false)
                refresh()
            },
            onAdd = { def -> add(def) },
        )
        b.wgList.layoutManager = LinearLayoutManager(requireContext())
        b.wgList.adapter = adapter

        b.size2x2.setOnClickListener { setSize("2x2") }
        b.size4x2.setOnClickListener { setSize("4x2") }
        b.size4x4.setOnClickListener { setSize("4x4") }


        store.addListener(listener)
        refresh()
    }

    private fun setSize(size: String) {
        store.settings.widgetLayout = size
        store.changed(reschedule = false)
        refresh()
    }

    /** Просит лаунчер поставить конкретный виджет на рабочий стол. */
    private fun add(def: WidgetDef) {
        val provider = Widgets.providerFor(def.id)
        if (provider == null) {
            Toast.makeText(requireContext(), R.string.widget_pin_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        pin(provider, def.id)
    }

    /** Просьба к лаунчеру добавить виджет; если он не умеет — показываем инструкцию. */
    private fun pin(provider: Class<*>, kind: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = AppWidgetManager.getInstance(requireContext())
            if (manager.isRequestPinAppWidgetSupported) {
                val id = ComponentName(requireContext(), provider)
                val ok = manager.requestPinAppWidget(id, null, null)
                if (ok) {
                    Toast.makeText(requireContext(), R.string.widget_pin_ok, Toast.LENGTH_SHORT).show()
                    return
                }
            }
        } else {
            val requestPin = Intent("android.appwidget.action.APPWIDGET_PICK")
            if (requestPin.resolveActivity(requireContext().packageManager) != null) {
                Toast.makeText(requireContext(), R.string.widget_hint, Toast.LENGTH_LONG).show()
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.widget_pin_ok)
            .setMessage(R.string.widget_pin_unavailable)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun refresh() {
        val size = store.settings.widgetLayout
        b.size2x2.isSelected = size == "2x2"
        b.size4x2.isSelected = size == "4x2"
        b.size4x4.isSelected = size == "4x4"
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        store.removeListener(listener)
        super.onDestroyView()
        _b = null
    }
}
