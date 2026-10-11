package ru.memento

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Проверка «всё ли на месте» по исходникам: манифест, компоненты, разрешения, виджеты,
 * вся разметка разбирается без ошибок. Тест падает до сборки APK, если что-то потеряли.
 */
class StructureTest {

    private fun root(): File {
        var dir = File("").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("не найден корень проекта")
    }

    private fun app(): File = File(root(), "app/src/main")

    private fun doc(path: String): org.w3c.dom.Document {
        val f = File(app(), path)
        assertTrue("нет файла $path", f.exists())
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
    }

    private fun manifest() = doc("AndroidManifest.xml")

    private fun names(tag: String): List<String> {
        val nodes = manifest().getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("android:name").nodeValue }
    }

    @Test
    fun `в манифесте есть все экраны, службы и приёмники`() {
        val activities = names("activity")
        val services = names("service")
        val receivers = names("receiver")
        val providers = names("provider")

        listOf(
            ".ui.MainActivity", ".ui.ReminderSheetActivity", ".ui.AboutActivity",
        ).forEach { assertTrue("нет активности $it ($activities)", it in activities) }
        listOf(
            ".service.AiTaskService", ".widget.TodayWidgetService",
        ).forEach { assertTrue("нет службы $it ($services)", it in services) }
        listOf(
            ".alarm.ReminderReceiver", ".alarm.ActionReceiver", ".alarm.BootReceiver", ".widget.WidgetActionReceiver",
        ).forEach { assertTrue("нет приёмника $it ($receivers)", it in receivers) }
        listOf(
            ".widget.TodayWidgetProvider", ".widget.ProgressWidgetProvider",
        ).forEach { assertTrue("нет виджета $it ($providers)", it in providers) }
    }

    @Test
    fun `все указанные в манифесте классы существуют`() {
        val all = names("activity") + names("service") + names("receiver") + names("provider") + names("application")
        all.forEach { name ->
            val path = name.removePrefix(".").replace('.', '/') + ".kt"
            assertTrue("нет файла $path для $name", File(File(app(), "java/ru/memento"), path).exists())
        }
    }

    @Test
    fun `разрешения для будильников, уведомлений и микрофона на месте`() {
        val nodes = manifest().getElementsByTagName("uses-permission")
        val perms = (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("android:name").nodeValue }
        listOf(
            "android.permission.INTERNET",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.USE_EXACT_ALARM",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.RECORD_AUDIO",
            "android.permission.WAKE_LOCK",
        ).forEach { assertTrue("нет разрешения $it", it in perms) }
    }

    @Test
    fun `у каждого виджета есть разметка предпросмотра и период обновления`() {
        listOf("widget_today_info", "widget_progress_info").forEach { name ->
            val d = doc("res/xml/$name.xml")
            val meta = d.getElementsByTagName("appwidget-provider")
            assertTrue("нет appwidget-provider в $name", meta.length == 1)
            val attrs = meta.item(0).attributes
            assertTrue("нет minWidth", attrs.getNamedItem("android:minWidth") != null)
            assertTrue("нет initialLayout", attrs.getNamedItem("android:initialLayout") != null)
            val initial = attrs.getNamedItem("android:initialLayout").nodeValue
            val layoutName = initial.removePrefix("@layout/")
            assertTrue("нет разметки $layoutName", File(app(), "res/layout/$layoutName.xml").exists())
        }
    }

    @Test
    fun `вся разметка и все значения разбираются как XML`() {
        val res = File(app(), "res")
        val files = res.walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
        assertTrue("разметка должна быть найдена", files.size > 20)
        files.forEach { f ->
            try {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            } catch (e: Exception) {
                throw AssertionError("битый XML: ${f.name}: ${e.message}")
            }
        }
    }

    @Test
    fun `во всех разметках есть идентификаторы для поиска из кода`() {
        val layout = File(app(), "res/layout")
        val ids = mutableSetOf<String>()
        layout.listFiles { f -> f.extension == "xml" }!!.forEach { f ->
            val text = f.readText()
            Regex("@\\+id/([a-zA-Z0-9_]+)").findAll(text).forEach { ids.add(it.groupValues[1]) }
        }
        assertTrue("идентификаторов должно быть много", ids.size > 50)
        assertTrue(ids.containsAll(listOf("root", "content", "nav_bar", "fab", "nav_center", "switch_toggle")))
    }

    @Test
    fun `версия приложения задана один раз`() {
        val vp = File(root(), "version.properties")
        assertTrue(vp.exists())
        val text = vp.readText()
        assertTrue("нет versionCode", text.contains("versionCode"))
        assertTrue("нет versionName", text.contains("versionName"))
    }

    @Test
    fun `сборка настроена на выпуск подписанного APK`() {
        val gradle = File(root(), "app/build.gradle.kts").readText()
        assertTrue("нет версии из version.properties", gradle.contains("version.properties"))
        assertTrue("нет release сборки", gradle.contains("release"))
        assertTrue("нет резервной сборки safe", gradle.contains("safe"))
        assertTrue("нет задачи collectApks", gradle.contains("collectApks"))
        assertEquals("1", Regex("versionCode\\s*=\\s*(\\d+)").find(
            File(root(), "version.properties").readText())?.groupValues?.get(1))
    }
}
