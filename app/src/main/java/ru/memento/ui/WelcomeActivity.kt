package ru.memento.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import ru.memento.R
import ru.memento.data.Store
import ru.memento.databinding.ActivityWelcomeBinding

/**
 * Первый запуск: регистрация. Человек вводит имя (почту — по желанию),
 * после этого создаётся полностью пустой профиль — без демо-записей.
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тема — до super.onCreate: так безопаснее на старых прошивках
        setTheme(R.style.Theme_Memento)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.wlScroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(b.wlScroll)

        b.wlStart.setOnClickListener { submit(b.wlName.text?.toString().orEmpty(), b.wlEmail.text?.toString().orEmpty()) }
        b.wlSkip.setOnClickListener { submit("", b.wlEmail.text?.toString().orEmpty()) }
        b.wlEmail.setOnEditorActionListener { _, _, _ ->
            submit(b.wlName.text?.toString().orEmpty(), b.wlEmail.text?.toString().orEmpty())
            true
        }
        b.wlName.requestFocus()
    }

    private fun submit(name: String, email: String) {
        val store = Store.get(this)
        store.register(name, email)
        store.changed(reschedule = false)
        b.wlStart.isEnabled = false
        b.wlStart.text = getString(R.string.welcome_loading)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
