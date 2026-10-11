package ru.memento

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import ru.memento.data.Store
import java.io.File
import java.io.FileOutputStream

/** Общие помощники тестов: данные, раскладка, скриншоты. */
object TestSupport {

    const val DP_W = 411
    const val DP_H = 891

    fun ctx(): Context = ApplicationProvider.getApplicationContext()

    /** Пустая база — для проверок создания и удаления. */
    fun store(): Store {
        Store.resetForTests()
        return Store.get(ctx()).also { it.clearForTests() }
    }

    /** Данные как на сайте: 5 событий, одно выполнено. */
    fun seed(): Store {
        val s = store()
        s.seed()
        s.settings.userName = "Александр"
        return s
    }

    fun idle() = shadowOf(Looper.getMainLooper()).idle()

    fun px(dp: Int, ctx: Context = ctx()): Int =
        (dp * ctx.resources.displayMetrics.density).toInt()

    /** Разложить view на экране телефона (411×891 dp). */
    fun layoutAt(view: View, wDp: Int = DP_W, hDp: Int = DP_H) {
        val ctx = view.context
        val w = px(wDp, ctx)
        val h = px(hDp, ctx)
        view.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        idle()
    }

    fun capture(view: View, name: String, wDp: Int = DP_W, hDp: Int = DP_H): File {
        layoutAt(view, wDp, hDp)
        val w = view.width.coerceAtLeast(1)
        val h = view.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getProperty("memento.screenshots.dir") ?: "screenshots")
        dir.mkdirs()
        val out = File(dir, name)
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }

    fun screenshotDir(): File = File(System.getProperty("memento.screenshots.dir") ?: "screenshots")

    fun quietLogs() {
        ShadowLog.stream = null
    }
}
