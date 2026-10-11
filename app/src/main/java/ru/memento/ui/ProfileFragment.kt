package ru.memento.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.memento.BuildConfig
import ru.memento.R
import ru.memento.ai.Pipeline
import ru.memento.data.Profile
import ru.memento.data.Store
import ru.memento.databinding.FragmentProfileBinding
import ru.memento.notify.Notifier
import ru.memento.ui.adapter.ProfileAdapter

/**
 * Профиль: имя, темы, виджеты, уведомления, фоновая работа ИИ, API-ключ XTROUTER,
 * состояние разрешений и служебные действия.
 */
class ProfileFragment : Fragment() {

    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!
    private lateinit var store: Store
    private lateinit var profiles: ProfileAdapter

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
    }

    private val listener: () -> Unit = { refreshValues() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _b = FragmentProfileBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        store = Store.get(requireContext())

        profiles = ProfileAdapter { p ->
            store.settings.activeProfileId = p.id
            store.settings.userName = p.name
            store.changed(reschedule = false)
            refreshValues()
        }
        b.pfProfiles.layoutManager = LinearLayoutManager(requireContext())
        b.pfProfiles.adapter = profiles

        // Имя
        b.pfUserName.setText(store.settings.userName)
        b.pfUserName.setOnFocusChangeListener { _, focused -> if (!focused) saveName() }
        b.pfUserName.setOnEditorActionListener { _, _, _ -> saveName(); true }

        // Размер виджета и тема
        b.pfSize2x2.setOnClickListener { setSize("2x2") }
        b.pfSize4x2.setOnClickListener { setSize("4x2") }
        b.pfSize4x4.setOnClickListener { setSize("4x4") }
        b.pfThemeGraphite.setOnClickListener { setTheme("graphite") }
        b.pfThemeCoal.setOnClickListener { setTheme("coal") }

        // Тумблеры
        b.setNotifications.switchTitle.setText(R.string.setting_notifications)
        b.setNotifications.switchSub.setText(R.string.setting_notifications_sub)
        b.setNotifications.switchToggle.isChecked = store.settings.notificationsEnabled
        b.setNotifications.switchToggle.setOnCheckedChangeListener { _, on ->
            store.settings.notificationsEnabled = on
            store.changed(reschedule = false)
        }

        b.setSound.switchTitle.setText(R.string.setting_sound)
        b.setSound.switchSub.setText(R.string.setting_background_sub)
        b.setSound.switchToggle.isChecked = store.settings.soundEnabled
        b.setSound.switchToggle.setOnCheckedChangeListener { _, on ->
            store.settings.soundEnabled = on
            Notifier.ensureChannels(requireContext())
            store.changed(reschedule = false)
        }

        b.setVerification.switchTitle.setText(R.string.setting_verification)
        b.setVerification.switchSub.setText(R.string.setting_verification_sub)
        b.setVerification.switchToggle.isChecked = store.settings.verification
        b.setVerification.switchToggle.setOnCheckedChangeListener { _, on ->
            store.settings.verification = on
            store.changed(reschedule = false)
        }

        b.setBackground.switchTitle.setText(R.string.setting_background)
        b.setBackground.switchSub.setText(R.string.setting_background_sub)
        b.setBackground.switchToggle.isChecked = store.settings.backgroundTasks
        b.setBackground.switchToggle.setOnCheckedChangeListener { _, on ->
            store.settings.backgroundTasks = on
            store.changed(reschedule = false)
        }

        b.setOffline.switchTitle.setText(R.string.setting_offline_speech)
        b.setOffline.switchSub.setText(R.string.setting_offline_speech_sub)
        b.setOffline.switchToggle.isChecked = store.settings.offlineSpeech
        b.setOffline.switchToggle.setOnCheckedChangeListener { _, on ->
            store.settings.offlineSpeech = on
            store.changed(reschedule = false)
        }

        // Новый профиль
        b.pfCreateProfile.setOnClickListener {
            val name = b.pfNewProfile.text?.toString()?.trim().orEmpty()
            if (name.length < 2) {
                Toast.makeText(requireContext(), R.string.new_profile_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val p = Profile(Store.uid("prof"), name)
            store.profiles.add(p)
            store.settings.activeProfileId = p.id
            store.settings.userName = name
            b.pfNewProfile.setText("")
            store.changed(reschedule = false)
            refreshValues()
        }

        // ИИ
        b.pfApiKey.setText(store.ai.apiKey)
        b.pfBaseUrl.setText(store.ai.baseUrl)
        b.pfModel.setText(store.ai.model)
        b.pfSaveAi.setOnClickListener { saveAi() }
        b.pfCheckKey.setOnClickListener { checkKey() }

        // Разрешения
        b.permNotifications.permTitle.setText(R.string.perm_notifications)
        b.permMic.permTitle.setText(R.string.perm_mic)
        b.permExact.permTitle.setText(R.string.perm_exact)
        b.permBattery.permTitle.setText(R.string.perm_battery)
        b.permNotifications.permBtn.setOnClickListener { askNotifications() }
        b.permMic.permBtn.setOnClickListener { askMic() }
        b.permExact.permBtn.setOnClickListener { askExact() }
        b.permBattery.permBtn.setOnClickListener { askBattery() }

        b.pfTestNotification.setOnClickListener {
            Notifier.showTest(requireContext())
            Toast.makeText(requireContext(), R.string.test_notification_sent, Toast.LENGTH_SHORT).show()
        }
        b.pfReset.setOnClickListener {
            store.resetDemo()
            Toast.makeText(requireContext(), R.string.reset_ok, Toast.LENGTH_SHORT).show()
            refreshValues()
        }
        b.pfAbout.setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
        b.pfDiagnostics.setOnClickListener { runSelfTest() }
        b.pfVersion.text = "${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} · сборка ${BuildConfig.VERSION_CODE}"

        store.addListener(listener)
        refreshValues()
    }

    /** Самопроверка: показывает по пунктам, что работает на этом телефоне. */
    private fun runSelfTest() {
        val lines = ru.memento.SelfTest.run(requireContext())
        val text = ru.memento.SelfTest.asText(lines)
        val body = lines.joinToString("\n") { (if (it.ok) "✓ " else "✗ ") + it.name + ": " + it.detail }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_Memento)
            .setTitle(R.string.diag_title)
            .setMessage(body)
            .setPositiveButton(R.string.diag_copy, null)
            .setNegativeButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Memento", text))
                Toast.makeText(requireContext(), R.string.diag_copied, Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun saveName() {
        val name = b.pfUserName.text?.toString()?.trim().orEmpty()
        if (name.isNotBlank() && name != store.settings.userName) {
            store.settings.userName = name
            store.profiles.firstOrNull { it.id == store.settings.activeProfileId }?.let { it.name = name } ?: run {
                store.profiles.add(Profile(store.settings.activeProfileId, name))
            }
            store.changed(reschedule = false)
            refreshValues()
        }
    }

    private fun setSize(size: String) {
        store.settings.widgetLayout = size
        store.changed(reschedule = false)
        refreshValues()
    }

    private fun setTheme(theme: String) {
        store.settings.theme = theme
        store.changed(reschedule = false)
        activity?.recreate()
    }

    private fun saveAi() {
        store.ai.apiKey = b.pfApiKey.text?.toString()?.trim().orEmpty()
        store.ai.baseUrl = b.pfBaseUrl.text?.toString()?.trim().orEmpty().ifBlank { "https://api.xkiro.com/v1" }
        store.ai.model = b.pfModel.text?.toString()?.trim().orEmpty().ifBlank { "qwen/qwen3.5-flash:free" }
        store.save()
        refreshValues()
        Toast.makeText(requireContext(), R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun checkKey() {
        saveAi()
        if (store.ai.apiKey.isBlank()) {
            Toast.makeText(requireContext(), R.string.key_missing, Toast.LENGTH_LONG).show()
            return
        }
        b.pfAiStatus.text = getString(R.string.key_checking)
        lifecycleScope.launch {
            val (ok, answer) = withContext(Dispatchers.IO) { Pipeline.ping(store) }
            if (!isAdded) return@launch
            b.pfAiStatus.text = if (ok) getString(R.string.key_ok, answer) else getString(R.string.key_fail, answer)
            Toast.makeText(
                requireContext(),
                if (ok) getString(R.string.key_ok, answer) else getString(R.string.key_fail, answer),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // ------------------------------- разрешения -------------------------------

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppSettings()
        }
    }

    private fun askMic() {
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun askExact() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${requireContext().packageName}")))
            } else {
                Toast.makeText(requireContext(), R.string.perm_granted, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), R.string.perm_granted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun askBattery() {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            Toast.makeText(requireContext(), R.string.perm_granted, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${requireContext().packageName}")),
            )
        }.onFailure { openAppSettings() }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun bindPermission(row: ru.memento.databinding.ViewPermissionRowBinding, ok: Boolean) {
        row.permState.setText(if (ok) R.string.perm_granted else R.string.perm_denied)
        row.permState.setTextColor(requireContext().getColor(if (ok) R.color.ok else R.color.err))
        row.permState.setBackgroundResource(if (ok) R.drawable.bg_pill_ok else R.drawable.bg_pill_err)
        row.permBtn.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun refreshPermissions() {
        bindPermission(b.permNotifications, Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS))
        bindPermission(b.permMic, granted(Manifest.permission.RECORD_AUDIO))
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true
        bindPermission(b.permExact, exact)
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        bindPermission(b.permBattery, pm.isIgnoringBatteryOptimizations(requireContext().packageName))
    }

    // ------------------------------- обновление -------------------------------

    private fun refreshValues() {
        val (done, total) = store.todayStats()
        val series = listOf(1, 3, 7, 14, 30).lastOrNull { it <= store.all().count { r -> r.done } } ?: 1
        b.pfName.text = store.settings.userName
        b.pfAvatar.text = store.settings.userName.take(1).uppercase()
        b.pfSeries.text = getString(R.string.series, series, store.all().count { it.done }, total)
        b.pfChipToday.text = "${getString(R.string.today)} ${store.today().size}"
        b.pfChipAll.text = "${getString(R.string.widget_all)} ${store.all().count { !it.done }}"
        profiles.submit(store.profiles, store.settings.activeProfileId)

        val size = store.settings.widgetLayout
        b.pfSize2x2.isSelected = size == "2x2"
        b.pfSize4x2.isSelected = size == "4x2"
        b.pfSize4x4.isSelected = size == "4x4"
        b.pfThemeGraphite.isSelected = store.settings.theme == "graphite"
        b.pfThemeCoal.isSelected = store.settings.theme == "coal"

        b.pfAiStatus.text = if (store.ai.ready) {
            "${store.ai.model} · ключ сохранён на телефоне"
        } else {
            getString(R.string.use_local_sub)
        }

        refreshPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    override fun onDestroyView() {
        store.removeListener(listener)
        super.onDestroyView()
        _b = null
    }
}
