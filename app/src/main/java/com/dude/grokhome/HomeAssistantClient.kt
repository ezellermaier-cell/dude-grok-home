package com.dude.grokhome

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional Home Assistant REST bridge.
 * Configure a Home Assistant URL and long-lived access token in DUDE settings.
 * Devices exposed to Home Assistant can then be controlled through their entity IDs.
 */
class HomeAssistantClient(private val baseUrl: String, private val token: String) {
    private fun request(path: String, method: String = "GET", body: JSONObject? = null): String {
        val url = URL(baseUrl.trimEnd('/') + path)
        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Home Assistant HTTP $code: $response")
            return response
        } finally { c.disconnect() }
    }

    fun check(): Boolean = request("/api/").contains("API")
    fun states(): String = request("/api/states")
    fun callService(domain: String, service: String, entityId: String): String =
        callServiceWithData(domain, service, entityId, JSONObject())

    fun callServiceWithData(domain: String, service: String, entityId: String, extra: JSONObject): String {
        extra.put("entity_id", entityId)
        return request("/api/services/$domain/$service", "POST", extra)
    }
}
