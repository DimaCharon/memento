package ru.memento.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ru.memento.R
import ru.memento.data.Store
import ru.memento.databinding.FragmentAssistantBinding
import ru.memento.service.AiTaskService
import ru.memento.ui.adapter.ChatAdapter
import ru.memento.ui.adapter.LogAdapter
import ru.memento.voice.VoiceInput

/**
 * Ассистент: голосовая (или текстовая) команда → распознавание в текст →
 * задача уходит в фоновую службу и досчитывается, даже если приложение закрыть.
 */
class AssistantFragment : Fragment() {

    private var _b: FragmentAssistantBinding? = null
    private val b get() = _b!!
    private lateinit var store: Store
    private lateinit var chat: ChatAdapter
    private lateinit var logs: LogAdapter
    private lateinit var voice: VoiceInput
    private var listening = false

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else setStatus(getString(R.string.voice_permission))
    }

    private val listener: () -> Unit = {
        chat.submit(store.chat)
        logs.submit(store.logs)
        b.aiChat.scrollToBottom()
        b.aiLogs.scrollToBottom()
        if (store.queue.isEmpty() && listening.not()) setStatus(getString(R.string.tap_and_speak))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _b = FragmentAssistantBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        store = Store.get(requireContext())
        voice = VoiceInput(requireContext())

        chat = ChatAdapter().apply { submit(store.chat) }
        logs = LogAdapter().apply { submit(store.logs) }
        b.aiChat.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        b.aiChat.adapter = chat
        b.aiLogs.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        b.aiLogs.adapter = logs

        b.aiBack.setOnClickListener {
            (activity as? MainActivity)?.show(MainActivity.TAB_HOME)
        }
        b.aiLogsToggle.setOnClickListener {
            val show = b.aiLogs.visibility != View.VISIBLE
            b.aiLogs.visibility = if (show) View.VISIBLE else View.GONE
            if (show) logs.submit(store.logs)
        }
        b.aiMic.setOnClickListener { onMic() }
        b.aiSend.setOnClickListener { send() }
        b.aiInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                send()
                true
            } else false
        }

        // Панель навигации плавает поверх экрана: оставляем ровно её высоту,
        // чтобы микрофон и поле ввода никогда не прятались под ней.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val nav = resources.getDimensionPixelSize(R.dimen.nav_bottom) +
                resources.getDimensionPixelSize(R.dimen.nav_height) +
                resources.getDimensionPixelSize(R.dimen.s2)
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, nav + bars.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(b.root)

        buildExamples()
        refreshModelLabel()
        store.addListener(listener)
    }

    override fun onResume() {
        super.onResume()
        if (store.chat.isEmpty()) {
            store.addMessage(
                "agent",
                "Привет! Скажите голосом: «напомни завтра в 9:00 встреча с командой» — я разберу фразу, " +
                    "создам напоминание и проверю результат по базе.",
                "NORY Orchestrator",
            )
            store.save()
        }
        chat.submit(store.chat)
        logs.submit(store.logs)
        b.aiChat.scrollToBottom()
        refreshModelLabel()
    }

    private fun refreshModelLabel() {
        b.aiModel.text = if (store.ai.ready) store.ai.model else getString(R.string.use_local_sub)
    }

    /** Примеры фраз — короткие подсказки, что можно сказать. */
    private fun buildExamples() {
        val row = b.aiExamples
        row.removeAllViews()
        val examples = listOf(
            "Напомни завтра в 9:00 встреча с командой",
            "Каждый день в 8 утра зарядка",
            "Что у меня сегодня?",
            "Отложи тренировку на 30 минут",
            "Поставь виджет 4×4",
            "Что на неделе?",
        )
        val pad = resources.getDimensionPixelSize(R.dimen.s2)
        examples.forEach { text ->
            val chip = TextView(requireContext())
            chip.text = text
            chip.setTextColor(requireContext().getColor(R.color.txt2))
            chip.textSize = 12.5f
            chip.background = ContextCompat.getDrawable(requireContext(), R.drawable.sel_chip)
            chip.setPadding(pad * 2, pad + 2, pad * 2, pad + 2)
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = pad }
            chip.layoutParams = lp
            chip.setOnClickListener { runCommand(text) }
            row.addView(chip)
        }
    }

    private fun onMic() {
        if (listening) {
            voice.stop()
            listening = false
            b.aiMic.setBackgroundResource(R.drawable.bg_mic_idle)
            b.aiMicIcon.tint(R.color.txt)
            setStatus(getString(R.string.tap_and_speak))
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startListening()
    }

    private fun startListening() {
        if (!voice.available) {
            setStatus(getString(R.string.voice_unavailable))
            return
        }
        listening = true
        b.aiMic.setBackgroundResource(R.drawable.bg_mic_active)
        b.aiMicIcon.tint(R.color.accentOn)
        setStatus(getString(R.string.listening))
        voice.start(store.settings.offlineSpeech, object : VoiceInput.Listener {
            override fun onPartial(text: String) {
                if (isAdded) setStatus(text)
            }

            override fun onResult(text: String) {
                listening = false
                if (!isAdded) return
                b.aiMic.setBackgroundResource(R.drawable.bg_mic_idle)
                b.aiMicIcon.tint(R.color.txt)
                setStatus(getString(R.string.recognizing) + " «$text»")
                runCommand(text)
            }

            override fun onError(message: String) {
                listening = false
                if (!isAdded) return
                b.aiMic.setBackgroundResource(R.drawable.bg_mic_idle)
                b.aiMicIcon.tint(R.color.txt)
                setStatus(message)
            }

            override fun onListening(listeningNow: Boolean) {
                if (isAdded && listeningNow) setStatus(getString(R.string.listening))
            }
        })
    }

    /** Фраза уходит в фоновую службу: её можно закрыть приложение — задача всё равно выполнится. */
    private fun runCommand(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        store.addMessage("user", clean)
        store.addLog("NORY Orchestrator", if (store.ai.ready) store.ai.model else "локальный движок", "run", "Задача принята: «$clean»")
        store.save()
        chat.submit(store.chat)
        b.aiChat.scrollToBottom()
        setStatus(getString(R.string.thinking))
        b.aiInput.setText("")
        AiTaskService.enqueue(requireContext(), clean, "Голос")
        if (store.settings.backgroundTasks) setStatus(getString(R.string.voice_queued))
    }

    private fun send() {
        val text = b.aiInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        runCommand(text)
    }

    private fun setStatus(text: String) {
        b.aiStatus.text = text
    }

    override fun onPause() {
        super.onPause()
        listening = false
        voice.stop()
    }

    override fun onDestroyView() {
        store.removeListener(listener)
        voice.release()
        super.onDestroyView()
        _b = null
    }
}
