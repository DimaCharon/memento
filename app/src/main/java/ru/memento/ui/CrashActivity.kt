package ru.memento.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.memento.CrashGuard
import ru.memento.R
import ru.memento.data.Store

/**
 * Экран «что случилось»: показывает текст сбоя простым текстом, без сложной разметки,
 * чтобы он открывался даже если остальной интерфейс сломан на конкретном телефоне.
 */
class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(EXTRA_REPORT) ?: CrashGuard.pendingReport(this) ?: "Сбой без подробностей."

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#0B0D11"))
        }

        root.addView(title(getString(R.string.crash_title), 22f, Color.parseColor("#ECEEF3")))
        root.addView(title(getString(R.string.crash_sub), 14f, Color.parseColor("#9BA2B0")))

        val scroller = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(0, pad, 0, pad)
        }
        val body = TextView(this).apply {
            text = report
            textSize = 12f
            setTextColor(Color.parseColor("#C8CEDD"))
            typeface = Typeface.MONOSPACE
            setTextIsolateOnApi26()
        }
        scroller.addView(body)
        root.addView(scroller)

        root.addView(button(getString(R.string.crash_copy)) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Memento", report))
            Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
        })
        root.addView(button(getString(R.string.crash_send)) {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_title))
                putExtra(Intent.EXTRA_TEXT, report)
            }
            runCatching { startActivity(Intent.createChooser(i, getString(R.string.crash_send))) }
        })
        root.addView(button(getString(R.string.crash_continue)) {
            CrashGuard.clear(this)
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        })
        root.addView(button(getString(R.string.crash_reset)) {
            runCatching {
                Store.resetForTests()
                java.io.File(filesDir, "memento.json").delete()
            }
            CrashGuard.clear(this)
            Toast.makeText(this, R.string.crash_reset_done, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        })

        setContentView(root)
    }

    private fun TextView.setTextIsolateOnApi26() {
        if (android.os.Build.VERSION.SDK_INT >= 26) textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
    }

    private fun title(text: String, size: Float, color: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        setPadding(0, 0, 0, (6 * resources.displayMetrics.density).toInt())
    }

    private fun button(text: String, action: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#0B0D11"))
        setBackgroundResource(R.drawable.bg_btn_light)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (48 * resources.displayMetrics.density).toInt(),
        ).also { it.topMargin = (8 * resources.displayMetrics.density).toInt() }
        isClickable = true
        setOnClickListener { action() }
    }

    companion object {
        private const val EXTRA_REPORT = "report"

        fun intent(context: Context, report: String): Intent =
            Intent(context, CrashActivity::class.java).putExtra(EXTRA_REPORT, report)
    }
}
