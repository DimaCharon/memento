package ru.memento.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Голосовой ввод: системный распознаватель речи (на телефоне, а не в облаке приложения).
 * При включённой настройке просим офлайн-модель — «переведёт в текст» даже без интернета.
 */
class VoiceInput(private val context: Context) {

    interface Listener {
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onError(message: String)
        fun onListening(listening: Boolean)
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var offline = true

    val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(offlineHint: Boolean, listener: Listener) {
        this.listener = listener
        this.offline = offlineHint
        if (!available) {
            listener.onError("распознавание речи недоступно")
            return
        }
        release()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onListening(true)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = listener.onListening(false)
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?.let { listener.onPartial(it) }
            }

            override fun onResults(results: Bundle?) {
                listener.onListening(false)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (text.isBlank()) listener.onError("не расслышал — попробуйте ещё раз") else listener.onResult(text)
            }

            override fun onError(error: Int) {
                listener.onListening(false)
                listener.onError(describe(error))
            }
        })
        r.startListening(intent())
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (offline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }.also {
        it.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, Locale.getDefault().language)
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun release() {
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "ошибка записи звука"
        SpeechRecognizer.ERROR_CLIENT -> "микрофон занят другим приложением"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "нет доступа к микрофону"
        SpeechRecognizer.ERROR_NETWORK -> "нужен интернет для распознавания"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "сеть не ответила"
        SpeechRecognizer.ERROR_NO_MATCH -> "не расслышал — повторите"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "распознаватель занят, попробуйте снова"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "не дождался речи"
        else -> "не удалось распознать речь (код $error)"
    }
}
