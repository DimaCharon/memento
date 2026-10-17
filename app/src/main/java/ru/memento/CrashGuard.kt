package ru.memento

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ловит любые сбои приложения и сохраняет их текст на телефон.
 * Тогда вместо «произошёл сбой» человек видит понятное сообщение и может его отправить.
 */
object CrashGuard {

    private const val PREFS = "memento_crash"
    private const val KEY_COUNT = "crashes"
    private const val KEY_PENDING = "pending_report"
    private const val KEY_LAST_OK = "last_success"

    @Volatile
    private var installed = false

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun file(context: Context): File = File(context.filesDir, "last_crash.txt")

    /** Ставится самой первой строкой в Application.onCreate, до всего остального. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(context, thread, error) }
            runCatching { previous?.uncaughtException(thread, error) }
        }
    }

    private fun save(context: Context, thread: Thread, error: Throwable) {
        val sw = StringWriter()
        PrintWriter(sw).use { error.printStackTrace(it) }
        val text = buildString {
            appendLine("NORY ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("время: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("телефон: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("поток: ${thread.name}")
            appendLine()
            appendLine(sw.toString())
        }
        runCatching { file(context).writeText(text) }
        runCatching {
            val log = File(context.filesDir, "crash_log.txt")
            val old = if (log.exists()) log.readText().takeLast(20_000) else ""
            log.writeText(text + "\n\n---\n" + old)
        }
        val p = prefs(context)
        runCatching { p.edit().putInt(KEY_COUNT, p.getInt(KEY_COUNT, 0) + 1).putBoolean(KEY_PENDING, true).apply() }
        android.util.Log.e("NORY", "СБОЙ ПРИЛОЖЕНИЯ: ${error.javaClass.name}: ${error.message}", error)
    }

    /** Не упал ли прошлый запуск. Если да — покажем текст ошибки человеку. */
    fun pendingReport(context: Context): String? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING, false)) return null
        val fromFile = runCatching { if (file(context).exists()) file(context).readText() else null }.getOrNull()
        return fromFile ?: "Сбой без подробностей. Приложение было закрыто системой."
    }

    fun crashes(context: Context): Int = prefs(context).getInt(KEY_COUNT, 0)

    fun tooManyCrashes(context: Context): Boolean = crashes(context) >= 2

    fun clear(context: Context) {
        runCatching { file(context).delete() }
        runCatching { prefs(context).edit().putBoolean(KEY_PENDING, false).putInt(KEY_COUNT, 0).apply() }
    }

    /** Запуск прошёл нормально: сбрасываем счётчик, чтобы «безопасный режим» не включался зря. */
    fun markHealthy(context: Context) {
        runCatching { prefs(context).edit().putBoolean(KEY_PENDING, false).putInt(KEY_COUNT, 0).apply() }
    }

    fun logFile(context: Context): File = File(context.filesDir, "crash_log.txt")

    const val LAST_OK_DEFAULT = true
    fun lastSuccess(context: Context): Boolean = prefs(context).getBoolean(KEY_LAST_OK, LAST_OK_DEFAULT)
    fun setLastSuccess(context: Context, ok: Boolean) {
        runCatching { prefs(context).edit().putBoolean(KEY_LAST_OK, ok).apply() }
    }
}
