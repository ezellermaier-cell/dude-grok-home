package com.dude.grokhome

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Home Assistant REST client. Vendor devices must first be integrated in Home Assistant. */
class HomeAssistantClient(private val baseUrl: String, private val token: String) {
    data class DeviceEntity(
        val entityId: String,
        val name: String,
        val state: String,
        val domain: String
    )

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): String {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "Home Assistant URL must start with http:// or https://"
        }
        require(token.isNotBlank()) { "Home Assistant token is missing" }
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val result = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Home Assistant returned HTTP $code")
            return result
        } finally {
            connection.disconnect()
        }
    }

    fun check(): Boolean = request("/api/").contains("API", ignoreCase = true)

    fun entities(): List<DeviceEntity> {
        val json = JSONArray(request("/api/states"))
        val supported = setOf("light", "switch", "fan", "cover", "climate", "vacuum", "lock", "media_player", "sensor")
        val result = ArrayList<DeviceEntity>()
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            val id = item.optString("entity_id")
            val domain = id.substringBefore(".", "")
            if (domain !in supported) continue
            val attributes = item.optJSONObject("attributes") ?: JSONObject()
            result.add(DeviceEntity(
                entityId = id,
                name = attributes.optString("friendly_name", id),
                state = item.optString("state", "unknown"),
                domain = domain
            ))
        }
        return result.sortedWith(compareBy<DeviceEntity> { it.domain }.thenBy { it.name.lowercase() })
    }

    fun states(): String = request("/api/states")

    fun callService(domain: String, service: String, entityId: String, extra: JSONObject = JSONObject()): String {
        require(domain.matches(Regex("[a-z_]+")) && service.matches(Regex("[a-z_]+"))) {
            "Invalid Home Assistant service"
        }
        extra.put("entity_id", entityId)
        return request("/api/services/$domain/$service", "POST", extra)
    }

    fun setClimate(entityId: String, temperature: Double? = null, mode: String? = null): String {
        val payload = JSONObject()
        if (temperature != null) payload.put("temperature", temperature)
        if (mode != null) payload.put("hvac_mode", mode)
        return callService("climate", "set_temperature", entityId, payload)
    }
}
