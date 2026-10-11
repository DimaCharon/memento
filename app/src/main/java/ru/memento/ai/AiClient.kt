package ru.memento.ai

import org.json.JSONArray
import org.json.JSONObject
import ru.memento.data.AiConfig
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Клиент OpenAI-совместимого шлюза (api.xkiro.com/v1).
 * Работает без сторонних библиотек — только HttpURLConnection, чтобы APK был маленьким.
 */
class AiClient(private val cfg: AiConfig) {

    data class Msg(val role: String, val content: String)

    /** Бросает AiException с человекочитаемой причиной. */
    fun chat(messages: List<Msg>, temperature: Double = 0.2, maxTokens: Int = 700): String {
        if (cfg.apiKey.isBlank()) throw AiException("не задан API-ключ")
        val url = URL(cfg.baseUrl.trimEnd('/') + "/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            setRequestProperty("Accept", "application/json")
        }
        val body = JSONObject().apply {
            put("model", cfg.model)
            put("messages", JSONArray().apply {
                messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }.toString()

        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                val msg = runCatching {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                        ?: JSONObject(text).optString("message")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"
                throw AiException(msg)
            }
            extractContent(text)
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException(e.message ?: "сеть недоступна")
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun extractContent(raw: String): String {
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw AiException("неожиданный ответ модели")
        val choices = json.optJSONArray("choices")
        val first = choices?.optJSONObject(0)
        val msg = first?.optJSONObject("message")
        val content = msg?.optString("content")
            ?: first?.optString("text")
            ?: ""
        if (content.isBlank()) throw AiException("пустой ответ модели")
        return content.trim()
    }

    /** Проверка ключа: короткий запрос «ответь ok». */
    fun testKey(): String {
        val answer = chat(
            listOf(
                Msg("system", "Ты — проверка соединения. Ответь одним словом: ok"),
                Msg("user", "ping"),
            ),
            temperature = 0.0,
            maxTokens = 16,
        )
        return answer.take(120)
    }

    class AiException(message: String) : Exception(message)
}

/** Достаёт JSON из ответа модели: снимает ```-заборы и лишний текст вокруг. */
fun extractJson(raw: String): JSONObject? {
    val cleaned = raw.replace("```json", "```").trim()
    val noFences = if (cleaned.contains("```")) cleaned.split("```").firstOrNull { it.contains("{") } ?: cleaned else cleaned
    val start = noFences.indexOf('{')
    val end = noFences.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching { JSONObject(noFences.substring(start, end + 1)) }.getOrNull()
}
