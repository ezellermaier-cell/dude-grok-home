package com.dude.grokhome

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small REST client for Gemini. The API key is supplied at runtime and is never
 * committed to the repository or placed in the request URL.
 */
class GeminiClient(private val apiKey: String) {
    data class Turn(val role: String, val text: String)

    fun reply(question: String, history: List<Turn>): String {
        require(apiKey.isNotBlank()) { "Add your Gemini API key in DUDE Settings." }
        val contents = JSONArray()
        history.takeLast(12).forEach { turn ->
            contents.put(JSONObject()
                .put("role", turn.role)
                .put("parts", JSONArray().put(JSONObject().put("text", turn.text))))
        }
        contents.put(JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", question))))

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put(
                "text",
                "You are DUDE, a friendly, witty, practical voice assistant living on a Samsung tablet. " +
                    "Answer general questions clearly and accurately. Be concise when speaking aloud, " +
                    "use a relaxed clever personality without being rude, and admit uncertainty. " +
                    "Do not claim to have controlled smart-home devices or accessed live information unless " +
                    "the app explicitly confirms it. Never invent current weather, news, or device states."
            ))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 700))

        val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = try { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty() } catch (_: Exception) { "" }
                throw IllegalStateException(when (code) {
                    400, 401, 403 -> "Gemini rejected the API key or request. Check the key in Settings."
                    429 -> "Gemini's free usage limit has been reached. Try again later."
                    else -> message.ifBlank { "Gemini is temporarily unavailable (HTTP $code)." }
                })
            }
            val root = JSONObject(raw)
            val candidates = root.optJSONArray("candidates")
            val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val answer = buildString {
                if (parts != null) for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i)
                    if (part != null) append(part.optString("text", ""))
                }
            }.trim()
            if (answer.isBlank()) throw IllegalStateException("Gemini returned an empty answer. Try asking another way.")
            return answer
        } finally {
            connection.disconnect()
        }
    }
}
