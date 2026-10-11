package ru.memento.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import ru.memento.BuildConfig
import ru.memento.R

/** Короткая справка: что умеет приложение и где что лежит. */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val pad = resources.getDimensionPixelSize(R.dimen.screen_h)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(getColor(R.color.bg0))
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME
            setTextColor(getColor(R.color.txt))
            textSize = 22f
        }
        val body = TextView(this).apply {
            text = getString(R.string.about_text, BuildConfig.VERSION_NAME) + "\n\n" +
                "• «+» — новое напоминание, долгое нажатие — ассистент\n" +
                "• Ассистент: голос → текст → действие, задачи досчитываются в фоне\n" +
                "• Виджеты на рабочий стол: «Сегодня» и «Прогресс дня»\n" +
                "• Настройки ИИ: Профиль → ИИ · XTROUTER (ключ хранится только на телефоне)\n\n" +
                getString(R.string.ai_section_sub)
            setTextColor(getColor(R.color.txt2))
            textSize = 14f
            setLineSpacing(0f, 1.25f)
        }
        root.addView(title)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val scroll = ScrollView(this).apply { addView(root) }
        val container = android.widget.FrameLayout(this).apply { addView(scroll) }
        setContentView(container)

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }
}
